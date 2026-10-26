#!/usr/bin/env python3
"""Fail-closed, read-only R2 evidence aggregator for single-node production readiness.

The source objects are untrusted data. This program only reads R2, verifies immutable
checksums and result contents, and writes local review artifacts. It never updates R2,
GitHub variables, the application database, or deployment state.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

PREFIX = "production/readiness-evidence/"
MANIFEST_NAME = "single-node-production-readiness.json"
REPORT_NAME = "readiness-aggregate-report.json"
SHA_RE = re.compile(r"^[0-9a-f]{64}$")
KEY_RE = re.compile(r"^production/readiness-evidence/[A-Za-z0-9._/-]{1,220}$")
CONTROL_RE = re.compile(r"^[A-Z][A-Z0-9_]{1,63}$")


class EvidenceError(Exception):
    pass


class EvidenceSource:
    def __init__(self, local_dir: Path | None, bucket: str = "", endpoint: str = ""):
        self.local_dir = local_dir
        self.bucket = bucket
        self.endpoint = endpoint.rstrip("/")

    def _aws(self, *args: str) -> bytes:
        env = os.environ.copy()
        env["AWS_EC2_METADATA_DISABLED"] = "true"
        env.setdefault("AWS_DEFAULT_REGION", "auto")
        env.setdefault("AWS_REGION", "auto")
        env.setdefault("AWS_PAGER", "")
        if self.endpoint and not self.endpoint.startswith("https://"):
            raise EvidenceError("R2 endpoint must use HTTPS")
        command = ["aws", "--no-cli-pager"]
        if self.endpoint:
            command += ["--endpoint-url", self.endpoint]
        command += list(args)
        try:
            proc = subprocess.run(command, env=env, stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE, check=False, timeout=90)
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise EvidenceError("R2 read operation failed (AWS CLI unavailable or timed out)") from exc
        if proc.returncode:
            # Avoid printing stderr; tool output may contain environmental details.
            raise EvidenceError("R2 read operation failed; verify read-only access and object existence")
        return proc.stdout

    def list_result_keys(self) -> list[str]:
        if self.local_dir is not None:
            return sorted(str(p.name) for p in self.local_dir.glob("*.result.json") if p.is_file())
        keys: list[str] = []
        token: str | None = None
        while True:
            args = ["s3api", "list-objects-v2", "--bucket", self.bucket,
                    "--prefix", PREFIX, "--output", "json"]
            if token:
                args += ["--continuation-token", token]
            raw = self._aws(*args)
            try:
                page = json.loads(raw)
            except (json.JSONDecodeError, UnicodeDecodeError) as exc:
                raise EvidenceError("R2 object listing returned invalid JSON") from exc
            if not isinstance(page, dict) or not isinstance(page.get("Contents", []), list):
                raise EvidenceError("R2 object listing response has an invalid shape")
            for row in page.get("Contents", []):
                if isinstance(row, dict) and isinstance(row.get("Key"), str):
                    key = row["Key"]
                    if key.endswith(".result.json"):
                        keys.append(key)
            if page.get("IsTruncated") is not True:
                break
            token = page.get("NextContinuationToken")
            if not isinstance(token, str) or not token:
                raise EvidenceError("R2 pagination is incomplete; refusing to certify")
        return sorted(set(keys))

    def get(self, key: str) -> bytes:
        if not KEY_RE.fullmatch(key) or ".." in key:
            raise EvidenceError("Object key is outside the protected readiness evidence prefix")
        if self.local_dir is not None:
            path = self.local_dir / Path(key).name
            try:
                return path.read_bytes()
            except OSError as exc:
                raise EvidenceError(f"Required local evidence object is missing: {Path(key).name}") from exc
        if not self.bucket or not self.endpoint:
            raise EvidenceError("R2_BUCKET and R2_ENDPOINT_URL are required")
        return self._aws("s3", "cp", f"s3://{self.bucket}/{key}", "-")

    def get_sidecar(self, key: str) -> bytes:
        return self.get(key + ".sha256")


def utc_datetime(value: Any, label: str) -> datetime:
    if not isinstance(value, str) or not value.strip():
        raise EvidenceError(f"{label} is missing")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise EvidenceError(f"{label} is not a valid ISO-8601 timestamp") from exc
    if parsed.tzinfo is None:
        raise EvidenceError(f"{label} must include a timezone")
    return parsed.astimezone(timezone.utc)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def checksum_value(sidecar: bytes, label: str) -> str:
    first = sidecar.decode("utf-8", errors="strict").strip().split()
    if not first or not SHA_RE.fullmatch(first[0]):
        raise EvidenceError(f"{label} is not a SHA-256 sidecar")
    return first[0]


def checked_bytes(source: EvidenceSource, key: str, expected_sha: str | None = None) -> tuple[bytes, str]:
    data = source.get(key)
    sidecar_hash = checksum_value(source.get_sidecar(key), f"{Path(key).name}.sha256")
    actual = sha256(data)
    if sidecar_hash != actual:
        raise EvidenceError(f"Checksum sidecar does not match object bytes: {Path(key).name}")
    if expected_sha is not None and expected_sha != actual:
        raise EvidenceError(f"Declared SHA-256 does not match object bytes: {Path(key).name}")
    return data, actual


def valid_object_key(value: Any) -> bool:
    return isinstance(value, str) and bool(KEY_RE.fullmatch(value)) and ".." not in value


def load_results(source: EvidenceSource, blockers: list[str]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    try:
        keys = source.list_result_keys()
    except EvidenceError as exc:
        blockers.append(str(exc))
        return records
    if not keys:
        blockers.append("No machine-readable readiness result JSON objects were found.")
        return records

    for raw_key in keys:
        key = raw_key if raw_key.startswith(PREFIX) else PREFIX + raw_key
        if not valid_object_key(key):
            blockers.append("An unsafe result-object key was listed; aggregation stopped for that object.")
            continue
        try:
            raw, result_sha = checked_bytes(source, key)
            data = json.loads(raw.decode("utf-8"))
            if not isinstance(data, dict):
                raise EvidenceError("result JSON root must be an object")
            if data.get("schemaVersion") != 1 or data.get("kind") != "readiness-evidence-result":
                raise EvidenceError("unrecognized readiness result schema")
            control = data.get("controlId")
            if not isinstance(control, str) or not CONTROL_RE.fullmatch(control):
                raise EvidenceError("controlId is missing or invalid")
            finished = utc_datetime(data.get("finishedAtUtc"), "finishedAtUtc")
            started = utc_datetime(data.get("startedAtUtc"), "startedAtUtc")
            if finished < started:
                raise EvidenceError("finishedAtUtc predates startedAtUtc")
            if data.get("status") not in {"PASS", "FAIL"}:
                raise EvidenceError("status must be PASS or FAIL")
            data["_objectKey"] = key
            data["_objectSha256"] = result_sha
            data["_finished"] = finished
            records.append(data)
        except (EvidenceError, json.JSONDecodeError, UnicodeDecodeError) as exc:
            blockers.append(f"Invalid result object {Path(key).name}: {exc}")
    return records


def latest_controls(records: list[dict[str, Any]], control_ids: set[str]) -> dict[str, dict[str, Any]]:
    by_control: dict[str, list[dict[str, Any]]] = {}
    for record in records:
        control = record["controlId"]
        if control in control_ids:
            by_control.setdefault(control, []).append(record)
    # A later FAIL deliberately supersedes an older PASS. Upload timestamps are not used.
    return {
        control: sorted(items, key=lambda item: (item["_finished"], item["_objectKey"]))[-1]
        for control, items in by_control.items()
    }


def nonempty_person(value: Any) -> bool:
    return isinstance(value, str) and len(value.strip()) >= 2 and len(value.strip()) <= 160


def validate_control(
    source: EvidenceSource,
    policy: dict[str, Any],
    control_spec: dict[str, Any],
    record: dict[str, Any] | None,
    release_sha: str,
    now: datetime,
) -> tuple[dict[str, Any], list[str]]:
    cid = control_spec["id"]
    problems: list[str] = []
    entry: dict[str, Any] = {"controlId": cid, "status": None, "resultKey": None, "resultSha256": None,
                             "evidenceKey": None, "evidenceSha256": None, "finishedAtUtc": None}
    if not record:
        problems.append(f"{cid}: no result is available in the evidence store")
        entry["status"] = "MISSING"
        return entry, problems

    entry.update(status=record.get("status"), resultKey=record.get("_objectKey"),
                 resultSha256=record.get("_objectSha256"), finishedAtUtc=record.get("finishedAtUtc"),
                 releaseSha=record.get("releaseSha"), reviewer=record.get("reviewer"))
    if record.get("status") != "PASS":
        problems.append(f"{cid}: latest recorded result is FAIL (older PASS results are ignored)")
    checks = record.get("checks")
    if not isinstance(checks, dict) or checks.get("failCount") != 0:
        problems.append(f"{cid}: result does not assert zero failing checks")
    if not nonempty_person(record.get("reviewer")):
        problems.append(f"{cid}: named operator/reviewer is missing")

    try:
        finished = record["_finished"]
        age_days = (now - finished).total_seconds() / 86400
        max_age = control_spec.get("maxAgeDays", policy["defaultMaxEvidenceAgeDays"])
        if age_days < -300:
            raise EvidenceError("timestamp is too far in the future")
        if age_days < 0:
            problems.append(f"{cid}: result timestamp is in the future")
        if age_days > int(max_age):
            problems.append(f"{cid}: evidence is older than {max_age} days")
        entry["ageDays"] = round(max(0, age_days), 3)
    except (TypeError, ValueError, EvidenceError) as exc:
        problems.append(f"{cid}: invalid evidence time: {exc}")

    if control_spec.get("requiresReleaseSha", False):
        if record.get("releaseSha") != release_sha:
            problems.append(f"{cid}: result release SHA does not match the qualified release")
    elif record.get("releaseSha") is not None and record.get("releaseSha") != release_sha:
        # When supplied, a different app SHA must not silently be treated as current.
        problems.append(f"{cid}: result is bound to a different release SHA")
    if cid == "PEN_TEST":
        if record.get("assessmentType") != "independent":
            problems.append("PEN_TEST: self-run scans cannot substitute for an independent penetration test")
        assessor = record.get("assessor")
        if not nonempty_person(assessor) or str(assessor).strip().casefold() == str(record.get("reviewer", "")).strip().casefold():
            problems.append("PEN_TEST: assessor must be named and differ from the operator")
        reviewer = record.get("independentReviewer")
        if not nonempty_person(reviewer):
            problems.append("PEN_TEST: independent report reviewer is missing")
        elif str(reviewer).strip().casefold() in {
            str(record.get("reviewer", "")).strip().casefold(),
            str(assessor or "").strip().casefold(),
        }:
            problems.append("PEN_TEST: independent reviewer must differ from operator and assessor")
    if cid in {"FINANCE_RECONCILIATION", "PRIVACY_REVIEW", "SINGLE_NODE_RECOVERY"}:
        independent = record.get("independentReviewer")
        if not nonempty_person(independent):
            problems.append(f"{cid}: independent reviewer is missing")
        elif str(independent).strip().casefold() == str(record.get("reviewer", "")).strip().casefold():
            problems.append(f"{cid}: independent reviewer must differ from the operator")

    evidence = record.get("evidence")
    if not isinstance(evidence, dict):
        problems.append(f"{cid}: report evidence metadata is missing")
        return entry, problems
    key = evidence.get("key")
    expected = evidence.get("sha256")
    if not valid_object_key(key):
        problems.append(f"{cid}: report object key is unsafe or outside the protected prefix")
        return entry, problems
    entry["evidenceKey"] = key
    entry["evidenceSha256"] = expected
    if not isinstance(expected, str) or not SHA_RE.fullmatch(expected):
        problems.append(f"{cid}: report SHA-256 is missing or invalid")
    else:
        try:
            _, actual = checked_bytes(source, key, expected)
            entry["evidenceSha256"] = actual
            entry["evidenceVerified"] = True
        except EvidenceError as exc:
            problems.append(f"{cid}: report verification failed: {exc}")
            entry["evidenceVerified"] = False

    if cid == "SINGLE_NODE_RECOVERY":
        metrics = record.get("metrics")
        if not isinstance(metrics, dict):
            problems.append("SINGLE_NODE_RECOVERY: metrics object is missing")
        else:
            for name, target in (("rpoSeconds", "measuredRpoSeconds"), ("rtoSeconds", "measuredRtoSeconds")):
                value = metrics.get(name)
                if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                    problems.append(f"SINGLE_NODE_RECOVERY: {name} must be an integer measured from the reviewed drill")
                else:
                    entry[target] = value
    return entry, problems


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    try:
        path.chmod(0o600)
    except OSError:
        pass


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-sha", required=True, help="Exact 40-character SHA in the passing qualification artifact")
    parser.add_argument("--policy", type=Path, default=Path("infra/certification/single-node-readiness-policy.json"))
    parser.add_argument("--output-dir", type=Path, default=Path("certification-evidence"))
    parser.add_argument("--local-results-dir", type=Path, help="Offline/test mode: read objects and sidecars from a local directory")
    args = parser.parse_args()

    output = args.output_dir
    manifest_path = output / MANIFEST_NAME
    report_path = output / REPORT_NAME
    # A failed rerun can never leave a previous PASS manifest behind.
    try:
        manifest_path.unlink(missing_ok=True)
    except OSError:
        print("Cannot remove previous readiness manifest; refusing to continue.", file=sys.stderr)
        return 2

    blockers: list[str] = []
    if not re.fullmatch(r"[0-9a-f]{40}", args.release_sha):
        blockers.append("--release-sha must be an exact lowercase 40-character Git SHA")
    try:
        policy = json.loads(args.policy.read_text(encoding="utf-8"))
        if not isinstance(policy, dict) or policy.get("policyId") != "single-node-production-readiness":
            raise EvidenceError("policy file identity is invalid")
        controls = policy.get("controls")
        if not isinstance(controls, list) or not controls:
            raise EvidenceError("policy controls must be a non-empty list")
        control_ids = {item["id"] for item in controls if isinstance(item, dict) and isinstance(item.get("id"), str)}
        if len(control_ids) != len(controls):
            raise EvidenceError("policy control IDs are missing or duplicated")
    except (OSError, json.JSONDecodeError, EvidenceError, KeyError) as exc:
        print(f"Readiness policy invalid: {exc}", file=sys.stderr)
        return 2

    if args.local_results_dir:
        source = EvidenceSource(args.local_results_dir)
    else:
        endpoint = os.environ.get("R2_ENDPOINT_URL", "")
        bucket = os.environ.get("R2_BUCKET", "")
        if not endpoint.startswith("https://") or not bucket:
            blockers.append("R2_BUCKET and HTTPS R2_ENDPOINT_URL are required")
        if not (os.environ.get("AWS_ACCESS_KEY_ID") or os.environ.get("AWS_PROFILE")):
            blockers.append("R2 read-only credentials are missing")
        source = EvidenceSource(None, bucket, endpoint)

    now = datetime.now(timezone.utc)
    records = load_results(source, blockers)
    latest = latest_controls(records, control_ids)
    checks: list[dict[str, Any]] = []
    by_id: dict[str, dict[str, Any]] = {}
    for spec in controls:
        cid = spec["id"]
        row, issues = validate_control(source, policy, spec, latest.get(cid), args.release_sha, now)
        checks.append(row)
        by_id[cid] = row
        blockers.extend(issues)

    recovery = by_id.get("SINGLE_NODE_RECOVERY", {})
    rpo = recovery.get("measuredRpoSeconds")
    rto = recovery.get("measuredRtoSeconds")
    max_rpo = policy.get("maxRpoSeconds")
    max_rto = policy.get("maxRtoSeconds")
    if isinstance(max_rpo, bool) or not isinstance(max_rpo, int) or max_rpo < 0:
        blockers.append("Readiness policy maxRpoSeconds is invalid")
    elif isinstance(rpo, int) and rpo > max_rpo:
        blockers.append(f"Measured RPO {rpo}s exceeds policy maximum {max_rpo}s")
    if isinstance(max_rto, bool) or not isinstance(max_rto, int) or max_rto < 0:
        blockers.append("Readiness policy maxRtoSeconds is invalid")
    elif isinstance(rto, int) and rto > max_rto:
        blockers.append(f"Measured RTO {rto}s exceeds policy maximum {max_rto}s")

    single = latest.get("SINGLE_NODE_RECOVERY")
    measurement_scope = single.get("scope") if isinstance(single, dict) else None
    if not isinstance(measurement_scope, str) or len(measurement_scope.strip()) < 30:
        blockers.append("SINGLE_NODE_RECOVERY: documented measurement scope (at least 30 characters) is required")

    evidence_map = {
        spec["manifestField"]: by_id[spec["id"]].get("evidenceKey")
        for spec in controls
    }
    result_map = {
        row["controlId"]: {
            "resultKey": row.get("resultKey"),
            "resultSha256": row.get("resultSha256"),
            "evidenceSha256": row.get("evidenceSha256"),
            "finishedAtUtc": row.get("finishedAtUtc"),
            "releaseSha": row.get("releaseSha"),
            "status": row.get("status"),
        }
        for row in checks
    }
    report = {
        "schemaVersion": 1,
        "kind": "single-node-readiness-aggregation-report",
        "status": "BLOCKED" if blockers else "PASS",
        "releaseSha": args.release_sha,
        "checkedAtUtc": now.isoformat(),
        "topology": policy.get("topology"),
        "nodeCount": policy.get("nodeCount"),
        "controls": checks,
        "blockers": sorted(set(blockers)),
        "productionCertificationGranted": False,
        "humanApprovalRequired": True,
        "writesToR2": False,
        "writesToGithubVariables": False,
        "deploymentPerformed": False,
    }
    write_json(report_path, report)
    if blockers:
        print("READINESS BLOCKED: evidence is incomplete, stale, mismatched, or failed.")
        for item in sorted(set(blockers)):
            print(f"- {item}")
        print(f"Diagnostic report written to {report_path}")
        return 2

    if not isinstance(rpo, int) or not isinstance(rto, int):
        print("READINESS BLOCKED: RPO/RTO measurements unavailable.", file=sys.stderr)
        return 2
    manifest = {
        "schemaVersion": 1,
        "releaseSha": args.release_sha,
        "status": "PASS",
        "readinessType": "single-node-production-readiness",
        "certifiedAtUtc": now.isoformat(),
        "topology": policy["topology"],
        "nodeCount": policy["nodeCount"],
        "measuredRpoSeconds": rpo,
        "measuredRtoSeconds": rto,
        "measurementScope": measurement_scope,
        "evidence": evidence_map,
        "evidenceResults": result_map,
        "attestedByProtectedEnvironment": True,
        "productionReadinessGranted": True,
        "enterpriseHACertificationGranted": False,
    }
    write_json(manifest_path, manifest)
    print("PASS: all selected evidence and checksums are verified.")
    print(f"Manifest written to {manifest_path}")
    print("This is a single-node attestation only; GitHub Environment approval and production workflow gates remain required.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except EvidenceError as exc:
        print(f"Evidence aggregation failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
