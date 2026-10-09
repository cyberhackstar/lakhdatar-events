const { defineConfig, devices } = require('@playwright/test');

const baseURL = process.env.E2E_BASE_URL;
if (!baseURL) throw new Error('E2E_BASE_URL is required');
const e2eEnv = (process.env.E2E_ENV || '').toLowerCase();
const target = new URL(baseURL);
const targetHost = target.hostname.toLowerCase();
if (targetHost === 'events.neelastack.com' || targetHost === 'www.events.neelastack.com') {
  throw new Error('Refusing live production origin');
}
if (e2eEnv === 'staging') {
  if (target.protocol !== 'https:') throw new Error('Staging E2E_BASE_URL must use HTTPS');
  if (targetHost !== 'staging-events.neelastack.com') {
    throw new Error('Staging browser qualification must target https://staging-events.neelastack.com');
  }
} else if (e2eEnv === 'local') {
  if (!['localhost', '127.0.0.1'].includes(targetHost)) {
    throw new Error('Local E2E must target localhost or 127.0.0.1');
  }
} else {
  throw new Error('E2E_ENV must be either local or staging');
}

module.exports = defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [['line'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'android-chrome', use: { ...devices['Pixel 7'] } },
    { name: 'ios-safari', use: { ...devices['iPhone 13'] } },
  ],
});
