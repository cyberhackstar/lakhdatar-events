import importlib.util
import unittest
from unittest.mock import patch
from datetime import datetime, timezone, timedelta
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("verify_production_readiness_artifact.py")
spec = importlib.util.spec_from_file_location("readiness_verifier", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

SHA = "0123456789abcdef0123456789abcdef01234567"


def valid_manifest():
    return {
        "schemaVersion": 1,
        "releaseSha": SHA,
        "status": "PASS",
        "readinessType": "single-node-production-readiness",
        "certifiedAtUtc": datetime.now(timezone.utc).isoformat(),
        "topology": "single-node",
        "nodeCount": 1,
        "evidence": {
            "singleNodeRecoveryDrill": "recovery-drill-2026-001",
            "offsiteBackup": "offsite-backup-review-2026-001",
            "pointInTimeRecovery": "pitr-drill-2026-001",
            "penetrationTest": "pentest-report-2026-001",
            "paymentRecovery": "payment-chaos-2026-001",
            "alerting": "alerting-test-2026-001",
            "financeReconciliation": "finance-recon-2026-001",
            "privacyReview": "privacy-review-2026-001",
            "privilegedMfa": "mfa-review-2026-001",
        },
        "attestedByProtectedEnvironment": True,
        "productionReadinessGranted": True,
        "enterpriseHACertificationGranted": False,
    }


class VerifyProductionReadinessArtifactTests(unittest.TestCase):
    def test_accepts_complete_matching_manifest(self):
        module.validate_manifest(valid_manifest(), SHA)

    def test_rejects_wrong_sha(self):
        with self.assertRaisesRegex(module.VerificationError, "releaseSha"):
            module.validate_manifest(valid_manifest(), "f" * 40)

    def test_rejects_non_pass_status(self):
        data = valid_manifest()
        data["status"] = "PENDING"
        with self.assertRaisesRegex(module.VerificationError, "status"):
            module.validate_manifest(data, SHA)

    def test_rejects_missing_evidence_identifier(self):
        data = valid_manifest()
        data["evidence"]["alerting"] = ""
        with self.assertRaisesRegex(module.VerificationError, "alerting"):
            module.validate_manifest(data, SHA)

    def test_rejects_wrong_topology_and_ha_grant(self):
        data = valid_manifest()
        data["topology"] = "enterprise-ha"
        with self.assertRaisesRegex(module.VerificationError, "topology"):
            module.validate_manifest(data, SHA)
        data = valid_manifest()
        data["enterpriseHACertificationGranted"] = True
        with self.assertRaisesRegex(module.VerificationError, "enterpriseHACertificationGranted"):
            module.validate_manifest(data, SHA)

    def test_rejects_stale_attestation(self):
        data = valid_manifest()
        data["certifiedAtUtc"] = (datetime.now(timezone.utc) - timedelta(days=91)).isoformat()
        with self.assertRaisesRegex(module.VerificationError, "older than"):
            module.validate_manifest(data, SHA)

    def test_rejects_future_attestation(self):
        data = valid_manifest()
        data["certifiedAtUtc"] = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
        with self.assertRaisesRegex(module.VerificationError, "future"):
            module.validate_manifest(data, SHA)


class VerifyFromGithubRunFilterTests(unittest.TestCase):
    """Regression: GitHub may return a run `path` with or without an '@ref' suffix."""

    def _run(self, path):
        return {"id": 1, "status": "completed", "conclusion": "success", "event": "workflow_dispatch",
                "head_branch": "main", "path": path, "created_at": datetime.now(timezone.utc).isoformat()}

    def _verify(self, path):
        responses = {
            "runs": {"workflow_runs": [self._run(path)]},
            "artifacts": {"artifacts": [{"id": 9, "name": f"single-node-production-readiness-{SHA}", "expired": False}]},
        }
        def fake_api(token, api_path):
            return responses["artifacts"] if "/artifacts" in api_path else responses["runs"]
        with patch.object(module, "_api_json", side_effect=fake_api), \
             patch.object(module, "_download_artifact_zip", return_value=b""), \
             patch.object(module, "_read_manifest", return_value=valid_manifest()):
            return module.verify_from_github("owner/repo", "token", SHA)

    def test_accepts_bare_and_ref_suffixed_paths(self):
        for path in (".github/workflows/single-node-production-readiness.yml",
                     ".github/workflows/single-node-production-readiness.yml@refs/heads/main"):
            with self.subTest(path=path):
                self.assertEqual(self._verify(path), (1, 9))

    def test_rejects_other_workflow_path(self):
        for path in (".github/workflows/evil.yml",
                     ".github/workflows/single-node-production-readiness.yml@refs/heads/evil"):
            with self.subTest(path=path):
                with self.assertRaises(module.VerificationError):
                    self._verify(path)


if __name__ == "__main__":
    unittest.main(verbosity=2)
