# Neelastack Events v1.9.42 — Production deployment

1. Use the normal GitHub Actions pipeline for v1.9.42.
2. Require the frontend baseline, production build, frontend tests, and backend `mvn -B -ntp clean verify` to pass before deployment.
3. Deploy the generated images using the existing production deployment workflow.
4. Do not add a `polyfills` property to the Angular `@angular/build:unit-test` target; test Zone.js bootstrap is provided through `tsconfig.spec.json` and `src/test-polyfills.ts`.
