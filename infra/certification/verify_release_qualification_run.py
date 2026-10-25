#!/usr/bin/env python3
"""Verify the provenance of a release-qualification workflow run before trusting its artifact."""
from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from typing import Any

API = "https://api.github.com"
EXPECTED_NAME = "Enterprise Release Qualification"
EXPECTED_PATH = ".github/workflows/enterprise-release-qualification.yml"
MAX_QUALIFICATION_AGE_DAYS = 30


class VerificationError(RuntimeError):
    pass


def parse_time(value: Any) -> datetime:
    if not isinstance(value, str) or not value:
        raise VerificationError("qualification run has no valid creation timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise VerificationError("qualification run has an invalid creation timestamp") from exc
    if parsed.tzinfo is None:
        raise VerificationError("qualification run timestamp must include a timezone")
    return parsed.astimezone(timezone.utc)


def _api_json(token: str, repo: str, run_id: str) -> dict[str, Any]:
    request = urllib.request.Request(
        f"{API}/repos/{repo}/actions/runs/{run_id}",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise VerificationError(f"GitHub Actions run lookup failed: {exc.__class__.__name__}") from exc
    if not isinstance(payload, dict):
        raise VerificationError("GitHub Actions run lookup returned an unexpected response")
    return payload


def verify_run(repo: str, token: str, run_id: str, *, now: datetime | None = None) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise VerificationError("GITHUB_REPOSITORY is invalid")
    if not re.fullmatch(r"[0-9]+", run_id):
        raise VerificationError("QUALIFICATION_RUN_ID must be numeric")
    if not token:
        raise VerificationError("GH_TOKEN is required")

    run = _api_json(token, repo, run_id)
    checks = {
        "id": int(run_id),
        "name": EXPECTED_NAME,
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "path": EXPECTED_PATH,
    }
    for field, expected in checks.items():
        if run.get(field) != expected:
            raise VerificationError(f"qualification run provenance mismatch for {field}")

    harness_sha = run.get("head_sha")
    if not isinstance(harness_sha, str) or not re.fullmatch(r"[0-9a-f]{40}", harness_sha):
        raise VerificationError("qualification run has an invalid harness commit SHA")

    created = parse_time(run.get("created_at"))
    current = now or datetime.now(timezone.utc)
    if created > current + timedelta(minutes=5):
        raise VerificationError("qualification run creation time is in the future")
    if current - created > timedelta(days=MAX_QUALIFICATION_AGE_DAYS):
        raise VerificationError(f"qualification run is older than {MAX_QUALIFICATION_AGE_DAYS} days; rerun qualification")

    return harness_sha


def main() -> int:
    token = os.environ.get("GH_TOKEN", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    run_id = os.environ.get("QUALIFICATION_RUN_ID", "")
    output_path = os.environ.get("GITHUB_OUTPUT", "")
    try:
        harness_sha = verify_run(repo, token, run_id)
        if not output_path:
            raise VerificationError("GITHUB_OUTPUT is required to pass the trusted harness SHA")
        with open(output_path, "a", encoding="utf-8") as output:
            output.write(f"harness_sha={harness_sha}\n")
        print(f"Verified successful release qualification run {run_id} from main; harness SHA is {harness_sha}.")
        return 0
    except (OSError, VerificationError) as exc:
        print(f"::error title=Release qualification provenance failed::{exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
