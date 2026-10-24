from __future__ import annotations

import unittest
from pathlib import Path

from check_production_content_readiness import validate_source


VALID = '''
LEGAL = {
  operator: 'Example Registered Hospitality Private Limited',
  address: 'Unit 4, Example Business Park, Jaipur, Rajasthan 302001, India',
  supportEmail: 'help@example.org',
  grievanceOfficer: 'Asha Example, Grievance Officer',
  grievanceEmail: 'grievance@example.org',
  refundProcessingDays: '5–7 business days',
}
'''


class ProductionContentReadinessTests(unittest.TestCase):
    def test_accepts_owner_supplied_complete_public_content(self):
        self.assertEqual(validate_source(VALID), [])

    def test_rejects_owner_todo_markers(self):
        issues = validate_source(VALID.replace("operator: 'Example Registered Hospitality Private Limited'", "operator: 'Neelastack', // TODO(owner): registered legal entity name"))
        self.assertTrue(any("TODO(owner)" in issue for issue in issues))
        self.assertTrue(any("registered legal entity" in issue for issue in issues))

    def test_rejects_city_only_address(self):
        issues = validate_source(VALID.replace("Unit 4, Example Business Park, Jaipur, Rajasthan 302001, India", "Jaipur, Rajasthan, India"))
        self.assertTrue(any("complete registered business address" in issue for issue in issues))

    def test_rejects_generic_grievance_role(self):
        issues = validate_source(VALID.replace("Asha Example, Grievance Officer", "Grievance Officer, Neelastack"))
        self.assertTrue(any("actual appointed" in issue for issue in issues))


if __name__ == "__main__":
    unittest.main()
