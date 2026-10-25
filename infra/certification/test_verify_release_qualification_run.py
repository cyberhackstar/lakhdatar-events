import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name("verify_release_qualification_run.py")
SPEC = importlib.util.spec_from_file_location("verify_release_qualification_run", MODULE_PATH)
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)

NOW = datetime(2026, 10, 24, 0, 0, tzinfo=timezone.utc)
GOOD_RUN = {
    "id": 123456,
    "name": "Enterprise Release Qualification",
    "event": "workflow_dispatch",
    "status": "completed",
    "conclusion": "success",
    "head_branch": "main",
    "path": ".github/workflows/enterprise-release-qualification.yml",
    "head_sha": "a" * 40,
    "created_at": (NOW - timedelta(hours=2)).isoformat(),
}


class VerifyReleaseQualificationRunTests(unittest.TestCase):
    def test_accepts_recent_successful_main_workflow(self):
        with patch.object(module, "_api_json", return_value=GOOD_RUN):
            self.assertEqual(module.verify_run("owner/repo", "test-token", "123456", now=NOW), "a" * 40)

    def test_rejects_non_main_or_untrusted_workflow_path(self):
        for field, value in [("head_branch", "feature"), ("path", ".github/workflows/evil.yml")]:
            with self.subTest(field=field):
                candidate = {**GOOD_RUN, field: value}
                with patch.object(module, "_api_json", return_value=candidate):
                    with self.assertRaises(module.VerificationError):
                        module.verify_run("owner/repo", "test-token", "123456", now=NOW)

    def test_rejects_failed_or_non_dispatch_run(self):
        for field, value in [("conclusion", "failure"), ("event", "push"), ("status", "in_progress")]:
            with self.subTest(field=field):
                candidate = {**GOOD_RUN, field: value}
                with patch.object(module, "_api_json", return_value=candidate):
                    with self.assertRaises(module.VerificationError):
                        module.verify_run("owner/repo", "test-token", "123456", now=NOW)

    def test_rejects_stale_or_malformed_run(self):
        for candidate in [
            {**GOOD_RUN, "created_at": (NOW - timedelta(days=31)).isoformat()},
            {**GOOD_RUN, "head_sha": "not-a-sha"},
        ]:
            with patch.object(module, "_api_json", return_value=candidate):
                with self.assertRaises(module.VerificationError):
                    module.verify_run("owner/repo", "test-token", "123456", now=NOW)

    def test_rejects_invalid_repository_and_run_id_before_api(self):
        with self.assertRaises(module.VerificationError):
            module.verify_run("owner/repo/extra", "test-token", "123456", now=NOW)
        with self.assertRaises(module.VerificationError):
            module.verify_run("owner/repo", "test-token", "123x", now=NOW)


if __name__ == "__main__":
    unittest.main()
