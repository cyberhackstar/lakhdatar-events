import importlib.util
import unittest
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


if __name__ == "__main__":
    unittest.main(verbosity=2)
