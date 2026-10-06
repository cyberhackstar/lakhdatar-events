# v2.0.14 — CI compilation hotfix and release-gate hardening

## Release purpose

v2.0.14 supersedes v2.0.12 after GitHub Actions exposed a Java syntax defect in `RefundService`.

## Fixed

- Fixed `RefundService.java` at the non-retryable refund error predicate: the closing parenthesis for the surrounding `ApiException` condition was missing.
- Kept the intended refund state-machine logic unchanged; this release corrects the compilation defect rather than changing refund semantics.
- Replaced CodeQL Java `autobuild` with the repository's deterministic Maven compile step so CodeQL and the primary backend CI compile the same source contract.
- Retained Java 21 and the full `mvn -B -ntp clean verify` backend gate.

## CI evidence addressed

The supplied CI logs show Java 21 was installed successfully, Maven reached compilation of 136 backend source files, and the only compiler error was `RefundService.java:[420,85] ')' expected`.
