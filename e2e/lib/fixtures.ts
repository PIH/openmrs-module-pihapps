import { test as base, expect, Page, Locator, BrowserContext, TestInfo } from '@playwright/test';
import { Creds, Instance, instanceFor } from './instances';
import { Rest, basic } from './rest';

export interface Session { page: Page; context: BrowserContext; errors: string[]; user: Creds; }

type Fixtures = {
  inst: Instance;
  rest: Rest;
  as: (user: Creds, opts?: { locale?: string; initScript?: () => void }) => Promise<Page>;
  shot: (page: Page | Locator, name: string) => Promise<void>;
};

export const test = base.extend<Fixtures>({
  inst: async ({}, use, testInfo) => { await use(instanceFor(testInfo.project.name)); },

  rest: async ({ inst }, use) => {
    const rest = new Rest(inst);
    await use(rest);
    await rest.dispose();
  },

  as: async ({ browser, inst }, use, testInfo) => {
    const sessions: Session[] = [];
    await use(async (user, opts = {}) => {
      const context = await browser.newContext({
        baseURL: inst.baseURL + '/',
        viewport: { width: 1400, height: 1000 },
        extraHTTPHeaders: { Authorization: basic(user) },
      });
      if (opts.initScript) await context.addInitScript(opts.initScript);
      const page = await context.newPage();
      const errors: string[] = [];
      page.on('pageerror', e => errors.push('pageerror: ' + e.message));
      page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
      page.on('response', r => { if (r.status() >= 400 && !/favicon/.test(r.url())) errors.push('HTTP ' + r.status() + ' ' + r.request().method() + ' ' + r.url()); });
      const body: any = { sessionLocation: inst.sessionLocation };
      body.locale = opts.locale ?? 'en';
      const r = await page.request.post(inst.baseURL + '/ws/rest/v1/session', { data: body });
      expect(r.status(), 'session for ' + user.username).toBeLessThan(300);
      sessions.push({ page, context, errors, user });
      return page;
    });
    for (const s of sessions) {
      if (s.errors.length) await testInfo.attach('browser-errors-' + s.user.username + '.txt', { body: s.errors.join('\n'), contentType: 'text/plain' });
      await s.context.close();
    }
  },

  shot: async ({}, use, testInfo) => {
    let n = 0;
    await use(async (target, name) => {
      n++;
      const label = String(n).padStart(2, '0') + '-' + name.replace(/[^a-zA-Z0-9_-]+/g, '-');
      const isPage = 'goto' in target;
      const body = isPage ? await (target as Page).screenshot({ fullPage: true }) : await (target as Locator).screenshot();
      await testInfo.attach(label + '.png', { body, contentType: 'image/png' });
    });
  },
});

export { expect };

/**
 * Sets pihapps.labs.requireResultValidation for the tests of the enclosing describe, restoring the instance's
 * default after them.
 */
export function withValidation(on: boolean, opts: { skip?: string[]; reason?: string } = {}) {
  const skipped = (name: string) => (opts.skip ?? []).includes(name);
  test.beforeAll(async ({}, testInfo) => {
    test.skip(skipped(testInfo.project.name), opts.reason ?? 'not run on this instance');
    const rest = new Rest(instanceFor(testInfo.project.name));
    await rest.setValidation(on);
    await rest.dispose();
  });
  test.afterAll(async ({}, testInfo) => {
    if (skipped(testInfo.project.name)) return;
    const inst = instanceFor(testInfo.project.name);
    const rest = new Rest(inst);
    await rest.setValidation(inst.validationDefault);
    await rest.dispose();
  });
}

export function onlyOn(testInfo: TestInfo, keys: string[], reason: string) {
  test.skip(!keys.includes(testInfo.project.name), reason);
}
