import { defineConfig } from '@playwright/test';
import { selectedSites } from './lib/instances';

// One project per site in E2E_SITES (default: all). See README.md.
export default defineConfig({
  testDir: './specs',
  timeout: 240_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,               // the tests share the instance (and the validation setting)
  retries: 0,
  reporter: [['html', { outputFolder: 'playwright-report', open: 'never' }], ['list']],
  globalSetup: './lib/global-setup.ts',
  globalTeardown: './lib/global-teardown.ts',
  use: {
    trace: 'on',
    screenshot: 'on',
    video: 'retain-on-failure',
    actionTimeout: 20_000,
    navigationTimeout: 60_000,
  },
  projects: selectedSites().map(i => ({ name: i.key, use: { baseURL: i.baseURL + '/' } })),
});
