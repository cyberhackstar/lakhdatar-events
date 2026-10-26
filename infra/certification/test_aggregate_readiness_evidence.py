#!/usr/bin/env python3
"""Offline tests for the read-only R2 readiness aggregator."""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "infra/certification/aggregate_readiness_evidence.py"
POLICY = ROOT / "infra/certification/single-node-readiness-policy.json"
RELEASE = "0123456789abcdef0123456789abcdef01234567"
NOW = datetime.now(timezone.utc)
CONTROL_IDS = [
    "SINGLE_NODE_RECOVERY", "OFFSITE_BACKUP", "PITR", "PEN_TEST",
    "PAYMENT_CHAOS", "ALERTING", "FINANCE_RECONCILIATION", "PRIVACY_REVIEW", "MFA",
]


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def put_with_sidecar(root: Path, filename: str, data: bytes) -> str:
    (root / filename).write_bytes(data)
    sha = digest(data)
    (root / (filename + ".sha256")).write_text(f"{sha}  {filename}\n", encoding="utf-8")
    return sha


def make_result(root: Path, control: str, *, status: str = "PASS",
                finished: datetime | None = None, release: str | None = None,
                rpo: int = 120, rto: int = 420, independent: str | None = None,
                assessment: str | None = None, assessor: str | None = None) -> Path:
    finished = finished or NOW
    started = finished - timedelta(minutes=2)
    report_name = f"report-{control.lower()}-{finished.strftime('%Y%m%dT%H%M%S%fZ')}.txt"
    report_bytes = (f"Actual reviewed evidence for {control}\nScope and expected/observed outcome.\n").encode()
    report_sha = put_with_sidecar(root, report_name, report_bytes)
    scope = "Existing staging recovery drill with explicit scope and limitations, no total VM replacement or tunnel cutover."
    if control != "SINGLE_NODE_RECOVERY":
        scope = f"Reviewed control {control}, target and observed result with explicit limitations documented."
    document = {
        "schemaVersion": 1,
        "kind": "readiness-evidence-result",
        "controlId": control,
        "item": control.lower(),
        "status": status,
        "startedAtUtc": started.isoformat(),
        "finishedAtUtc": finished.isoformat(),
        "releaseSha": release if release is not None else (RELEASE if control in {
            "SINGLE_NODE_RECOVERY", "PEN_TEST", "PAYMENT_CHAOS", "FINANCE_RECONCILIATION", "PRIVACY_REVIEW", "MFA"
        } else None),
        "reviewer": "Operator One",
        "independentReviewer": independent if independent is not None else ({
            "SINGLE_NODE_RECOVERY": "Recovery Reviewer",
            "FINANCE_RECONCILIATION": "Finance Reviewer",
            "PRIVACY_REVIEW": "Privacy Reviewer",
            "PEN_TEST": "Pentest Reviewer",
        }.get(control)),
        "assessor": assessor or ("External Firm" if control == "PEN_TEST" else None),
        "assessmentType": assessment or ("independent" if control == "PEN_TEST" else None),
        "scope": scope,
        "evidence": {"key": f"production/readiness-evidence/{report_name}",
                     "fileName": report_name, "sha256": report_sha},
        "metrics": {"rpoSeconds": rpo, "rtoSeconds": rto} if control == "SINGLE_NODE_RECOVERY" else {},
        "checks": {"passCount": 3, "failCount": 0 if status == "PASS" else 1},
        "productionCertificationGranted": False,
        "humanApprovalRequired": True,
    }
    result_name = f"{control.lower()}-{finished.strftime('%Y%m%dT%H%M%S%fZ')}.result.json"
    put_with_sidecar(root, result_name, (json.dumps(document, sort_keys=True) + "\n").encode())
    return root / result_name


class AggregateReadinessTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / "source"
        self.out = Path(self.tmp.name) / "out"
        self.root.mkdir()
        for control in CONTROL_IDS:
            make_result(self.root, control)

    def tearDown(self):
        self.tmp.cleanup()

    def run_aggregator(self):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--release-sha", RELEASE,
             "--policy", str(POLICY), "--output-dir", str(self.out),
             "--local-results-dir", str(self.root)],
            capture_output=True, text=True, check=False,
        )

    def test_all_current_verified_records_create_single_node_manifest(self):
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        manifest = json.loads((self.out / "single-node-production-readiness.json").read_text())
        self.assertEqual(manifest["status"], "PASS")
        self.assertEqual(manifest["releaseSha"], RELEASE)
        self.assertEqual(manifest["topology"], "single-node")
        self.assertEqual(manifest["nodeCount"], 1)
        self.assertEqual(manifest["measuredRpoSeconds"], 120)
        self.assertEqual(manifest["measuredRtoSeconds"], 420)
        self.assertEqual(len(manifest["evidence"]), 9)
        self.assertFalse(manifest["enterpriseHACertificationGranted"])
        self.assertTrue(manifest["attestedByProtectedEnvironment"])
        self.assertTrue((self.out / "readiness-aggregate-report.json").is_file())

    def test_latest_fail_supersedes_older_pass_and_removes_stale_manifest(self):
        self.assertEqual(self.run_aggregator().returncode, 0)
        make_result(self.root, "PAYMENT_CHAOS", status="FAIL", finished=NOW + timedelta(seconds=1))
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2, proc.stdout + proc.stderr)
        self.assertIn("latest recorded result is FAIL", proc.stdout)
        self.assertFalse((self.out / "single-node-production-readiness.json").exists())
        diagnostic = json.loads((self.out / "readiness-aggregate-report.json").read_text())
        self.assertEqual(diagnostic["status"], "BLOCKED")

    def test_failure_wins_when_two_results_share_timestamp_precision(self):
        make_result(self.root, "PAYMENT_CHAOS", status="FAIL", finished=NOW)
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2, proc.stdout + proc.stderr)
        self.assertIn("latest recorded result is FAIL", proc.stdout)
        self.assertFalse((self.out / "single-node-production-readiness.json").exists())

    def test_wrong_release_sha_is_blocked(self):
        make_result(self.root, "PAYMENT_CHAOS", finished=NOW + timedelta(seconds=1),
                    release="f" * 40)
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2)
        self.assertIn("release SHA does not match", proc.stdout)
        self.assertFalse((self.out / "single-node-production-readiness.json").exists())

    def test_missing_control_is_blocked(self):
        for path in self.root.glob("mfa-*.result.json*"):
            path.unlink()
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2)
        self.assertIn("MFA: no result", proc.stdout)
        self.assertFalse((self.out / "single-node-production-readiness.json").exists())

    def test_result_sidecar_tampering_is_blocked(self):
        sidecar = next(self.root.glob("payment_chaos-*.result.json.sha256"))
        sidecar.write_text("0" * 64 + "  bad.json\n")
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2)
        self.assertTrue(any(word in proc.stdout for word in ("sidecar", "Invalid result object")))

    def test_missing_independent_reviewer_blocks_finance(self):
        make_result(self.root, "FINANCE_RECONCILIATION", independent="", finished=NOW + timedelta(seconds=1))
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2)
        self.assertIn("independent reviewer is missing", proc.stdout)

    def test_rpo_rto_limits_are_fail_closed(self):
        make_result(self.root, "SINGLE_NODE_RECOVERY", rpo=301, finished=NOW + timedelta(seconds=1))
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2)
        self.assertIn("Measured RPO 301s exceeds policy maximum 300s", proc.stdout)

    def test_stale_evidence_is_blocked_even_when_uploaded_recently(self):
        # Remove the fresh setup fixture first; a stale result cannot supersede a newer valid result.
        for path in self.root.glob("pitr-*.result.json*"):
            path.unlink()
        make_result(self.root, "PITR", finished=NOW - timedelta(days=8))
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2)
        self.assertIn("PITR: evidence is older than 7 days", proc.stdout)

    def test_self_run_scan_cannot_be_used_as_pentest(self):
        make_result(self.root, "PEN_TEST", assessment="self-run", finished=NOW + timedelta(seconds=1))
        proc = self.run_aggregator()
        self.assertEqual(proc.returncode, 2)
        self.assertIn("self-run scans cannot substitute", proc.stdout)

    def test_audit_only_never_creates_or_deletes_a_certification_manifest(self):
        self.out.mkdir(parents=True, exist_ok=True)
        existing_manifest = self.out / "single-node-production-readiness.json"
        existing_manifest.write_text('{"status":"existing-operator-file"}', encoding="utf-8")
        proc = subprocess.run(
            [sys.executable, str(SCRIPT), "--audit-only", "--policy", str(POLICY),
             "--output-dir", str(self.out), "--local-results-dir", str(self.root)],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        report = json.loads((self.out / "readiness-aggregate-report.json").read_text())
        self.assertEqual(report["status"], "AUDIT_PASS")
        self.assertTrue(report["auditOnly"])
        self.assertFalse(report["productionCertificationGranted"])
        self.assertEqual(existing_manifest.read_text(encoding="utf-8"), '{"status":"existing-operator-file"}')

    def test_r2_mode_requires_endpoint_and_bucket(self):
        proc = subprocess.run(
            [sys.executable, str(SCRIPT), "--release-sha", RELEASE, "--policy", str(POLICY),
             "--output-dir", str(self.out)],
            env={"PATH": str(Path(sys.executable).parent) + ":/usr/bin:/bin", "PYTHONIOENCODING":"utf-8"},
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(proc.returncode, 2)
        self.assertIn("R2_EVIDENCE_BUCKET and HTTPS R2_ENDPOINT_URL are required", proc.stdout)
        self.assertFalse((self.out / "single-node-production-readiness.json").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
