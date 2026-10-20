import assert from "node:assert/strict";
import fs from "node:fs";

const config = fs.readFileSync("e2e/playwright.config.js", "utf8");
const standalone = fs.readFileSync(".github/workflows/e2e-staging.yml", "utf8");
const qualification = fs.readFileSync(".github/workflows/enterprise-release-qualification.yml", "utf8");

assert.match(config, /reporter:\s*\[\['line'\],\s*\['html',\s*\{\s*outputFolder:\s*'playwright-report'/,
  "Playwright must keep the line and HTML reporters enabled");
assert.match(config, /trace:\s*'retain-on-failure'/, "Playwright must retain traces on failures");

for (const [name, workflow] of [["staging E2E", standalone], ["release qualification", qualification]]) {
  assert.match(workflow, /if:\s*always\(\)/, `${name} must upload evidence even when the test step fails`);
  assert.match(workflow, /path:\s*\|[\r\n]+\s*e2e\/playwright-report\/[\r\n]+\s*e2e\/test-results\//,
    `${name} must upload both the HTML report and Playwright test-results/ traces`);
  assert.match(workflow, /if-no-files-found:\s*warn/, `${name} must warn when expected test artifacts are missing`);
}
assert.doesNotMatch(qualification, /npm test -- --reporter=line/,
  "release qualification must not override the configured HTML reporter with line-only output");
console.log("PASS: staging browser workflows retain HTML reports, failure traces, and diagnostic artifacts.");
