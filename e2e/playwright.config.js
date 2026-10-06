const { defineConfig, devices } = require('@playwright/test');

const baseURL = process.env.E2E_BASE_URL;
if (!baseURL) throw new Error('E2E_BASE_URL is required');
if (!/^https:\/\//i.test(baseURL)) throw new Error('E2E_BASE_URL must use HTTPS');
if ((process.env.E2E_ENV || '').toLowerCase() !== 'staging') throw new Error('E2E_ENV=staging is required; browser qualification must never target production');
const targetHost = new URL(baseURL).hostname.toLowerCase();
if (targetHost === 'events.neelastack.com' || targetHost === 'www.events.neelastack.com') throw new Error('Refusing live production origin');

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
