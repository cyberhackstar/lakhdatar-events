#!/usr/bin/env python3
"""Fail-closed check for legal/customer-facing launch content.

This intentionally cannot invent or validate a registered legal identity for the owner.
It checks that the explicit TODO markers and known generic placeholders have been replaced
before enterprise release qualification can be published.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

DEFAULT_PATH = Path("frontend/src/app/features/legal/legal.config.ts")
FIELD_RE = re.compile(r"\b([A-Za-z][A-Za-z0-9]*):\s*(['\"])(.*?)\2", re.DOTALL)
EMAIL_RE = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")


def validate_source(source: str) -> list[str]:
    issues: list[str] = []
    if re.search(r"TODO\s*\(\s*owner\s*\)", source, re.IGNORECASE):
        issues.append("legal.config.ts still contains TODO(owner) entries; confirm and complete every marked legal/contact/refund field")
    fields = {match.group(1): match.group(3).strip() for match in FIELD_RE.finditer(source)}
    required = ("operator", "address", "supportEmail", "grievanceOfficer", "grievanceEmail", "refundProcessingDays")
    for key in required:
        if not fields.get(key):
            issues.append(f"legal.config.ts is missing a non-empty {key} field")

    operator = fields.get("operator", "")
    if operator.casefold() in {"neelastack", "lakhdatar events", "tbd", "replace me"}:
        issues.append("operator must be the actual registered legal entity name, not the product/brand placeholder")

    address = fields.get("address", "")
    if address.casefold() in {"jaipur, rajasthan, india", "india", "tbd", "replace me"} or len(address) < 20:
        issues.append("address must be the complete registered business address, not only a city/region")

    for key in ("supportEmail", "grievanceEmail"):
        value = fields.get(key, "")
        if value.casefold().endswith("@example.com") or not EMAIL_RE.fullmatch(value):
            issues.append(f"{key} must be a real monitored email address")

    officer = fields.get("grievanceOfficer", "").casefold()
    if officer in {"grievance officer, neelastack", "grievance officer", "tbd", "not appointed", "replace me"}:
        issues.append("grievanceOfficer must identify the actual appointed person/role approved by the owner")

    refund = fields.get("refundProcessingDays", "")
    if refund.casefold() in {"tbd", "to be confirmed", "replace me", ""}:
        issues.append("refundProcessingDays must be confirmed against actual payment/refund operations")
    return issues


def main() -> int:
    source_path = DEFAULT_PATH
    if len(sys.argv) == 3 and sys.argv[1] == "--source":
        source_path = Path(sys.argv[2])
    elif len(sys.argv) != 1:
        print("Usage: check_production_content_readiness.py [--source path]", file=sys.stderr)
        return 2
    try:
        source = source_path.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"::error::Cannot read production legal content config ({exc.__class__.__name__})", file=sys.stderr)
        return 2
    issues = validate_source(source)
    if issues:
        for issue in issues:
            print(f"::error::{issue}", file=sys.stderr)
        print("Production release qualification is blocked until the owner supplies and verifies these public legal/business facts.", file=sys.stderr)
        return 2
    print("Production legal/contact/refund content contains no known TODOs or generic placeholders.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
