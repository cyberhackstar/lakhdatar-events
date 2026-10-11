import fs from 'node:fs';
import assert from 'node:assert/strict';

const nginx = fs.readFileSync(new URL('../edge/nginx.conf', import.meta.url), 'utf8');
const haNginx = fs.readFileSync(new URL('../infra/ha/nginx-ha.conf.example', import.meta.url), 'utf8');
const zap = fs.readFileSync(new URL('../infra/security/zap-baseline.conf', import.meta.url), 'utf8');
const dast = fs.readFileSync(new URL('../infra/security/dast-staging.sh', import.meta.url), 'utf8');
const ssr = fs.readFileSync(new URL('../frontend/src/server.ts', import.meta.url), 'utf8');
const provision = fs.readFileSync(new URL('../e2e/support/provision.js', import.meta.url), 'utf8');
const runner = fs.readFileSync(new URL('../infra/loadtest/auto-runner.cjs', import.meta.url), 'utf8');
const catalog = fs.readFileSync(new URL('../infra/loadtest/catalog.js', import.meta.url), 'utf8');
const checkoutLoadScripts = ['checkout.js', 'checkout-hot-sale.js', 'checkout-idempotency.js'].map(name => fs.readFileSync(new URL(`../infra/loadtest/${name}`, import.meta.url), 'utf8'));
const loadSuite = fs.readFileSync(new URL('../infra/loadtest/run-suite.sh', import.meta.url), 'utf8');
const qualificationWorkflow = fs.readFileSync(new URL('../.github/workflows/enterprise-release-qualification.yml', import.meta.url), 'utf8');
const loadWorkflow = fs.readFileSync(new URL('../.github/workflows/load-test.yml', import.meta.url), 'utf8');
const cashfreeProvider = fs.readFileSync(new URL('../backend/src/main/java/com/neelastack/lakhdatar/service/CashfreeGatewayProvider.java', import.meta.url), 'utf8');
function locationBlock(source, declaration, label) {
  const start = source.indexOf(declaration);
  assert.notEqual(start, -1, `${label} location must exist`);
  const end = source.indexOf('\n    }', start);
  assert.notEqual(end, -1, `${label} location must have a closing brace`);
  return source.slice(start, end);
}

const edgeServerStart = nginx.indexOf('server {');
const edgeHealth = nginx.indexOf('location = /edge-health', edgeServerStart);
assert.ok(edgeServerStart >= 0 && edgeHealth > edgeServerStart, 'edge server block must be present');
const edgeServerHeaders = nginx.slice(edgeServerStart, edgeHealth);
const edgeExactCatalog = locationBlock(nginx, 'location = /api/v1/public/events {', 'exact catalogue');
const edgeCatalogTree = locationBlock(nginx, 'location ^~ /api/v1/public/events/ {', 'catalogue subtree');
assert.match(edgeServerHeaders, /add_header\s+Strict-Transport-Security\s+"max-age=31536000; includeSubDomains" always;/i, 'edge server must emit HSTS');
assert.match(edgeExactCatalog, /add_header\s+Strict-Transport-Security/i, 'exact catalogue cache location must set HSTS because child add_header replaces server inheritance');
assert.match(edgeCatalogTree, /add_header\s+Strict-Transport-Security/i, 'catalogue subtree cache location must set HSTS because child add_header replaces server inheritance');
assert.equal((nginx.match(/add_header\s+Strict-Transport-Security/ig) || []).length, 3, 'edge must set HSTS exactly at server level and in both cache locations');
assert.match(nginx, /proxy_hide_header\s+Strict-Transport-Security/i, 'upstream HSTS must be hidden to avoid duplicates');

const haServerStart = haNginx.indexOf('server {');
const haEdgeHealth = haNginx.indexOf('location = /edge-health', haServerStart);
assert.ok(haServerStart >= 0 && haEdgeHealth > haServerStart, 'HA edge server block must be present');
const haServerHeaders = haNginx.slice(haServerStart, haEdgeHealth);
const haExactCatalog = locationBlock(haNginx, 'location = /api/v1/public/events {', 'HA exact catalogue');
const haCatalogTree = locationBlock(haNginx, 'location ^~ /api/v1/public/events/ {', 'HA catalogue subtree');
assert.match(haServerHeaders, /add_header\s+Strict-Transport-Security\s+"max-age=31536000; includeSubDomains" always;/i, 'HA edge must emit HSTS');
assert.match(haExactCatalog, /add_header\s+Strict-Transport-Security/i, 'HA exact catalogue cache location must set HSTS');
assert.match(haCatalogTree, /add_header\s+Strict-Transport-Security/i, 'HA catalogue subtree cache location must set HSTS');
assert.equal((haNginx.match(/add_header\s+Strict-Transport-Security/ig) || []).length, 3, 'HA edge must set HSTS exactly at server level and in both cache locations');
assert.match(haNginx, /proxy_hide_header\s+Strict-Transport-Security/i, 'HA edge must hide upstream HSTS too');
for (const [label, source] of [['edge', nginx], ['HA edge', haNginx]]) {
  assert.match(source, /style-src 'self' 'nonce-\$request_id'; style-src-attr 'unsafe-inline';/, `${label} CSP must nonce inline style elements and isolate the narrow style-attribute allowance`);
  assert.doesNotMatch(source, /style-src 'self' 'unsafe-inline'/, `${label} CSP must not broadly allow inline style elements`);
  assert.match(source, /proxy_set_header X-CSP-Nonce \$request_id;/, `${label} must override the inbound CSP nonce with its per-request request id`);
  assert.match(source, /sub_filter '<style' '<style nonce=\"\$request_id\"';/, `${label} must nonce SSR style elements`);
  assert.match(source, /sub_filter '<app-root' '<app-root ngCspNonce=\"\$request_id\"';/, `${label} must provide the nonce to Angular client-side rendering`);
  assert.match(source, /proxy_set_header Accept-Encoding \"\";/, `${label} must request uncompressed upstream HTML for nonce rewriting`);
}
assert.doesNotMatch(nginx, /img-src[^;]*\shttps:\s/i, 'CSP img-src must not permit every HTTPS origin');
assert.match(nginx, /connect-src[^;]*https:\/\/sandbox\.cashfree\.com/i, 'Cashfree sandbox must be explicit in connect-src');
assert.match(ssr, /no-store, no-cache, must-revalidate, private/);
assert.match(ssr, /Pragma', 'no-cache/);
assert.match(provision, /E2E_FIXTURE_PROFILE/);
assert.match(runner, /verify-invariants\.sh/, 'stateful load runs must verify database invariants');
assert.match(runner, /E2E_STAGING_PROVISIONING_CONFIG/, 'fixture provisioning must reuse the existing E2E secret');
assert.match(runner, /delete loadEnv\.STAGING_DEPLOY_SSH_KEY/, 'SSH private key must not reach k6 subprocesses');
assert.doesNotMatch(catalog, /TICKET_TOKEN|ADMIN_BEARER|admin\/events|public\/tickets/, 'public catalogue load must not hammer protected APIs with one shared credential');
for (const [index, source] of checkoutLoadScripts.entries()) {
  assert.doesNotMatch(source, /new URL\s*\(/, `checkout load script ${index + 1} must not depend on URL global missing in k6`);
  assert.match(source, /BASE_URL_HOST_MATCH/, `checkout load script ${index + 1} must parse the base host compatibly with k6`);
  assert.match(source, /BASE_URL_HOST === 'events\.neelastack\.com' \|\| BASE_URL_HOST\.endsWith\('\.events\.neelastack\.com'\)/, `checkout load script ${index + 1} must use the exact production events hostname guard`);
  assert.match(source, /endsWith\('\.events\.neelastack\.com'\)/, `checkout load script ${index + 1} must also protect production event subdomains`);
}
const idempotencyScript = checkoutLoadScripts[2];
assert.match(idempotencyScript, /rate:\s*4,\s*timeUnit:\s*'1s',\s*duration:\s*'1s'/, 'idempotency probe must stay below the minimum per-email limiter while issuing a concurrent burst');
assert.match(idempotencyScript, /customerName:\s*'Idempotency Load Test'/, 'idempotency retries must reuse the exact same customer name');
assert.match(idempotencyScript, /customerEmail:\s*'idempotency-load@example\.com'/, 'idempotency retries must reuse the exact same syntactically ordinary reserved example.com test email');
for (const [index, source] of checkoutLoadScripts.entries()) {
  assert.match(source, /customerPhone:\s*'9876543210'/, `checkout load script ${index + 1} must supply Cashfree's 10-digit customer_phone`);
  assert.doesNotMatch(source, /customerPhone:\s*null/, `checkout load script ${index + 1} must not omit Cashfree's required customer_phone`);
}
assert.match(idempotencyScript, /safeErrorCode\(r\)/, 'idempotency diagnostics must include a safe API error code for unexpected responses');
assert.match(cashfreeProvider, /safeProviderErrorCode\(response\.body\(\)\)/, 'Cashfree provider failures must extract a safe machine-readable provider code');
assert.match(cashfreeProvider, /"provider\.error_code", providerErrorCode/, 'Cashfree provider error code must be emitted as structured metadata');
assert.doesNotMatch(cashfreeProvider, /EnterpriseLog\.(?:warn|error)\([^;]*response\.body\(\)/s, 'raw provider error response bodies must never be logged');
assert.doesNotMatch(idempotencyScript, /customer(?:Name|Email):\s*`[^`]*\$\{__(?:VU|ITER)/, 'idempotency retries must not mutate the original checkout payload');
assert.match(checkoutLoadScripts[0], /rate:\s*1,\s*timeUnit:\s*'6s',\s*duration:\s*'4m'/, 'success-path checkout probe must remain below the default per-client rate limit');
assert.match(checkoutLoadScripts[1], /http\.setResponseCallback\(http\.expectedStatuses\(200, 201, 409, 429, 503\)\)/, 'hot-sale load must classify designed overload/provider outcomes as expected responses');
assert.match(checkoutLoadScripts[1], /checkout_unavailable_503:\s*\['rate<0\.02'\]/, 'hot-sale load must still fail if the service returns too many 503 responses');
assert.match(checkoutLoadScripts[1], /unavailableResponses\.add\(response\.status === 503\)/, 'hot-sale load must measure 503 responses separately from intended 429 rate limiting');
assert.match(loadSuite, /chmod 0777 \"\$REPORT_ABS\"/, 'k6 summary mount must be writable to the pinned unprivileged container user');
assert.match(loadSuite, /trap restore_report_permissions EXIT/, 'load-test report directory permissions must be restored');
assert.match(qualificationWorkflow, /E2E_STAGING_PROVISIONING_CONFIG/);
assert.match(loadWorkflow, /E2E_STAGING_PROVISIONING_CONFIG/);
assert.doesNotMatch(qualificationWorkflow + loadWorkflow, /secrets\.LOADTEST_(?:EVENT_ID|TICKET_ID|TICKET_TOKEN|ADMIN_BEARER|STAFF_BEARER|CHECKIN_QR_TOKENS|DATABASE_URL)/, 'manual per-fixture credentials must not be required');
assert.doesNotMatch(qualificationWorkflow + loadWorkflow, /secrets\.STAGING_ENV_FILE/, 'deployment env file must not enter qualification jobs');
assert.match(zap, /^10055\s+WARN/m, 'CSP wildcard warnings must stay blocking until actually fixed');
assert.match(zap, /^10035\s+WARN/m, 'duplicate HSTS warnings must stay blocking until actually fixed');
assert.match(dast, /zap-baseline\.conf:\/zap\/wrk\/zap-baseline\.conf:ro/, 'ZAP config must be mounted into the container');
assert.match(dast, /-c zap-baseline\.conf/, 'ZAP must use the mounted config path');
console.log('Security and automatic-fixture source contracts passed.');
