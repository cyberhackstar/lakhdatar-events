#!/usr/bin/env python3
"""Fail-closed verifier for the protected single-node readiness artifact.

The production workflow uses this instead of a manually maintained
SINGLE_NODE_CERTIFIED_SHA GitHub Environment variable. It requires a successful
workflow run from main, a matching release-specific artifact, a valid manifest,
all nine evidence identifiers, and a recent attestation.
"""
from __future__ import annotations

import io
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime, timezone, timedelta
from typing import Any

API = "https://api.github.com"
WORKFLOW = "single-node-production-readiness.yml"
ARTIFACT_PREFIX = "single-node-production-readiness-"
MAX_ATTESTATION_AGE_DAYS = 90
REQUIRED_EVIDENCE = (
    "singleNodeRecoveryDrill",
    "offsiteBackup",
    "pointInTimeRecovery",
    "penetrationTest",
    "paymentRecovery",
    "alerting",
    "financeReconciliation",
    "privacyReview",
    "privilegedMfa",
)


class VerificationError(RuntimeError):
    pass


def _parse_time(raw: Any) -> datetime:
    if not isinstance(raw, str) or not raw:
        raise VerificationError("readiness manifest is missing certifiedAtUtc")
    try:
        result = datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError as exc:
        raise VerificationError("readiness manifest has invalid certifiedAtUtc") from exc
    if result.tzinfo is None:
        raise VerificationError("certifiedAtUtc must include a timezone")
    return result.astimezone(timezone.utc)


def validate_manifest(data: Any, release_sha: str, *, now: datetime | None = None) -> None:
    if not isinstance(data, dict):
        raise VerificationError("readiness manifest must be a JSON object")
    expected = {
        "schemaVersion": 1,
        "releaseSha": release_sha,
        "status": "PASS",
        "readinessType": "single-node-production-readiness",
        "topology": "single-node",
        "nodeCount": 1,
        "attestedByProtectedEnvironment": True,
        "productionReadinessGranted": True,
        "enterpriseHACertificationGranted": False,
    }
    for key, value in expected.items():
        if data.get(key) != value:
            raise VerificationError(f"readiness manifest mismatch for {key}")

    evidence = data.get("evidence")
    if not isinstance(evidence, dict):
        raise VerificationError("readiness manifest has no evidence object")
    for name in REQUIRED_EVIDENCE:
        value = evidence.get(name)
        if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9._:/-]{6,256}", value):
            raise VerificationError(f"readiness manifest has missing/invalid evidence identifier: {name}")

    current = now or datetime.now(timezone.utc)
    certified_at = _parse_time(data.get("certifiedAtUtc"))
    if certified_at > current + timedelta(minutes=5):
        raise VerificationError("readiness attestation timestamp is in the future")
    if current - certified_at > timedelta(days=MAX_ATTESTATION_AGE_DAYS):
        raise VerificationError(f"readiness attestation is older than {MAX_ATTESTATION_AGE_DAYS} days")


def _api_json(token: str, path: str) -> Any:
    request = urllib.request.Request(
        f"{API}{path}",
        headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"},
    )
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise VerificationError(f"GitHub API request failed for {path}: {exc.__class__.__name__}") from exc


def _download_artifact_zip(token: str, repo: str, artifact_id: int) -> bytes:
    path = f"/repos/{repo}/actions/artifacts/{artifact_id}/zip"
    request = urllib.request.Request(
        f"{API}{path}",
        headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"},
    )
    # Do not forward the API bearer token to GitHub's signed object-storage URL.
    opener = urllib.request.build_opener(_NoRedirect)
    try:
        try:
            response = opener.open(request, timeout=25)
            with response:
                return response.read()
        except urllib.error.HTTPError as exc:
            if exc.code not in (301, 302, 303, 307, 308):
                raise VerificationError(f"GitHub artifact download failed with HTTP {exc.code}") from exc
            location = exc.headers.get("Location")
            if not location:
                raise VerificationError("GitHub artifact download returned no redirect location") from exc
            with urllib.request.urlopen(urllib.request.Request(location), timeout=60) as response:
                return response.read()
    except (urllib.error.URLError, TimeoutError) as exc:
        raise VerificationError(f"GitHub artifact download failed: {exc.__class__.__name__}") from exc


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _read_manifest(archive_bytes: bytes) -> Any:
    try:
        with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
            candidates = [name for name in archive.namelist() if name.endswith("single-node-production-readiness.json") and not name.endswith("/")]
            if len(candidates) != 1:
                raise VerificationError("readiness artifact must contain exactly one single-node-production-readiness.json")
            with archive.open(candidates[0]) as source:
                return json.loads(source.read().decode("utf-8"))
    except (zipfile.BadZipFile, json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise VerificationError("readiness artifact is not a valid ZIP/JSON manifest") from exc


def verify_from_github(repo: str, token: str, release_sha: str) -> tuple[int, int]:
    if not re.fullmatch(r"[0-9a-f]{40}", release_sha):
        raise VerificationError("RELEASE_SHA must be a 40-character lowercase Git SHA")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise VerificationError("GITHUB_REPOSITORY is invalid")
    encoded_workflow = urllib.parse.quote(WORKFLOW, safe="")
    cutoff = datetime.now(timezone.utc) - timedelta(days=MAX_ATTESTATION_AGE_DAYS)
    inspected_runs = 0

    # The artifact is named with the exact app SHA, even if the workflow harness SHA differs.
    for page in range(1, 6):
        runs = _api_json(token, f"/repos/{repo}/actions/workflows/{encoded_workflow}/runs?status=success&per_page=100&page={page}")
        workflow_runs = runs.get("workflow_runs", []) if isinstance(runs, dict) else []
        if not workflow_runs:
            break
        stop_for_age = True
        for run in workflow_runs:
            created = _parse_time(run.get("created_at"))
            if created >= cutoff:
                stop_for_age = False
            else:
                continue
            if run.get("status") != "completed" or run.get("conclusion") != "success" or run.get("event") != "workflow_dispatch":
                continue
            if run.get("head_branch") != "main" or not str(run.get("path", "")).endswith("@refs/heads/main"):
                continue
            inspected_runs += 1
            run_id = run.get("id")
            if not isinstance(run_id, int):
                continue
            artifacts = _api_json(token, f"/repos/{repo}/actions/runs/{run_id}/artifacts")
            items = artifacts.get("artifacts", []) if isinstance(artifacts, dict) else []
            for artifact in items:
                if artifact.get("name") != f"{ARTIFACT_PREFIX}{release_sha}" or artifact.get("expired") is True:
                    continue
                artifact_id = artifact.get("id")
                if not isinstance(artifact_id, int):
                    continue
                data = _read_manifest(_download_artifact_zip(token, repo, artifact_id))
                validate_manifest(data, release_sha)
                print(f"Verified matching single-node readiness artifact from successful main workflow run {run_id}.")
                print("Nine evidence identifiers and the protected-environment attestation are present; the separate human production-environment approval remains required.")
                return run_id, artifact_id
        if stop_for_age:
            break
    raise VerificationError(f"No successful, recent, matching readiness artifact found for release SHA {release_sha} (inspected {inspected_runs} eligible runs)")


def main() -> int:
    if len(sys.argv) == 3 and sys.argv[1] == "--validate-manifest":
        release_sha = os.environ.get("RELEASE_SHA", "")
        try:
            with open(sys.argv[2], encoding="utf-8") as handle:
                validate_manifest(json.load(handle), release_sha)
            print("Readiness manifest valid.")
            return 0
        except (OSError, json.JSONDecodeError, VerificationError) as exc:
            print(f"ERROR: {exc}", file=sys.stderr)
            return 2
    token = os.environ.get("GH_TOKEN", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    release_sha = os.environ.get("RELEASE_SHA", "")
    if not token or not repo or not release_sha:
        print("ERROR: GH_TOKEN, GITHUB_REPOSITORY and RELEASE_SHA are required", file=sys.stderr)
        return 2
    try:
        verify_from_github(repo, token, release_sha)
        return 0
    except VerificationError as exc:
        # Deliberately do not print server response bodies, URLs containing signed tokens, or credentials.
        print(f"::error title=Production readiness gate failed::{exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
