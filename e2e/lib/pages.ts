import { Page, Locator, expect } from '@playwright/test';

// ---------- Lab ID page (labResultsByLabId.page) ----------

export async function openLabId(page: Page, patient: string, mode: 'enter' | 'validate', labId?: string) {
  const url = 'pihapps/labs/labResultsByLabId.page?patient=' + patient + '&mode=' + mode + (labId ? '&labId=' + encodeURIComponent(labId) : '');
  await page.goto(url);
  await waitLabIdLoaded(page);
}

export async function waitLabIdLoaded(page: Page) {
  await expect(page.locator('#lr-content .lr-order, #lr-content .lr-empty, #lr-content .lr-errors:not(:empty), .lr-empty').first()).toBeVisible({ timeout: 30000 });
  await page.waitForLoadState('networkidle');
  // Reference ranges and editors load asynchronously
  await page.waitForTimeout(500);
}

/** The block of one order on the Lab ID page, found by its order number (the name's title). */
export function lrOrder(page: Page, orderNumber: string): Locator {
  return page.locator('.lr-order', { has: page.locator('[title="' + orderNumber + '"]') });
}

export function resultRow(scope: Page | Locator, conceptUuid: string): Locator {
  return scope.locator('.result-row[data-concept-uuid="' + conceptUuid + '"]').first();
}

export async function fillResult(scope: Page | Locator, conceptUuid: string, value: string) {
  const f = resultRow(scope, conceptUuid).locator('.result-value-field').first();
  await f.fill(value);
  await f.dispatchEvent('change');
  await f.blur();
}

export async function setLrStatus(block: Locator, status: 'IN_PROGRESS' | 'COMPLETED' | 'EXCEPTION') {
  await block.locator('.lr-status-cell button[data-value="' + status + '"]').first().click();
}

/** Records the toast messages of the page in sessionStorage, so they can be read after the page reloads. */
export async function recordToasts(page: Page) {
  await page.evaluate(() => {
    const w = window as any;
    sessionStorage.removeItem('e2eToasts');
    const keep = (m: string) => { const a = JSON.parse(sessionStorage.getItem('e2eToasts') || '[]'); a.push(m); sessionStorage.setItem('e2eToasts', JSON.stringify(a)); };
    for (const f of ['successMessage', 'errorMessage']) {
      const orig = w.emr?.[f];
      if (orig) w.emr[f] = function (m: string) { keep(f + ': ' + m); return orig.apply(this, arguments); };
    }
  });
}

export async function toasts(page: Page): Promise<string[]> {
  return page.evaluate(() => JSON.parse(sessionStorage.getItem('e2eToasts') || '[]'));
}

/** Clicks Save on the Lab ID page; resolves to 'reloaded' when the page reloads after saving, or 'errors' otherwise. */
export async function saveLabId(page: Page): Promise<'reloaded' | 'errors'> {
  await recordToasts(page);
  // Errors of an earlier attempt would be taken for this one's
  await page.evaluate(() => document.querySelectorAll('.lr-errors').forEach(e => { e.innerHTML = ''; }));
  const nav = page.waitForEvent('framenavigated', { timeout: 30000 }).then(() => 'reloaded' as const);
  const err = page.locator('.lr-errors').filter({ hasText: /\S/ }).first().waitFor({ timeout: 30000 }).then(() => 'errors' as const);
  await page.locator('.lr-actions button.confirm').first().click();
  const result = await Promise.race([nav, err]);
  if (result === 'reloaded') await waitLabIdLoaded(page);
  return result;
}

export async function lrErrors(page: Page): Promise<string> {
  return (await page.locator('.lr-errors').allInnerTexts()).join(' | ').trim();
}

// ---------- Lab order list (labOrderList.page) and the single order form ----------

export async function openOrderList(page: Page, opts: { statuses?: string[]; labId?: string; patientGrouping?: boolean } = {}) {
  const params = (opts.statuses ?? []).map(s => 'status=' + s);
  if (opts.patientGrouping) params.push('grouping=patient');
  await page.goto('pihapps/labs/labOrderList.page' + (params.length ? '?' + params.join('&') : ''));
  await page.waitForLoadState('networkidle');
  if (opts.labId) {
    await page.locator('#lab-id-filter').fill(opts.labId);
    await page.locator('#lab-id-filter').blur();   // fires change (once): a later blur would reload the table again
    await page.waitForLoadState('networkidle');
    const rows = page.locator(opts.patientGrouping ? '#patients-table tbody tr' : '#orders-table tbody tr');
    await expect.poll(async () => {
      const texts = await rows.allInnerTexts();
      return texts.length > 0 && (opts.patientGrouping || texts.every(t => t.includes(opts.labId!)));
    }, { timeout: 20000 }).toBe(true);
  }
  await page.waitForTimeout(1000);
}

/** Filters the lab order list on the patient (the patient field's hidden value, as choosing from its search sets it). */
export async function filterOrderListOnPatient(page: Page, patientUuid: string) {
  await page.evaluate((uuid) => { (window as any).jQuery('#patient-filter-field').val(uuid).change(); }, patientUuid);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(800);
}

export function singleForm(page: Page): Locator { return page.locator('#lab-results-section'); }

export async function openSingleForm(page: Page, orderUuid: string) {
  await page.locator('.enter-results-action[data-order-uuid="' + orderUuid + '"]').first().click();
  const form = singleForm(page);
  await expect(form.locator('.fulfiller-status .select-buttons')).toBeVisible({ timeout: 20000 });
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(500);
  return form;
}

export async function singleStatus(page: Page, label: RegExp | string) {
  await singleForm(page).locator('.fulfiller-status .select-buttons button', { hasText: label }).first().click();
}

export async function singleStatusLabels(page: Page): Promise<string[]> {
  return (await singleForm(page).locator('.fulfiller-status .select-buttons button').allInnerTexts()).map(s => s.trim());
}

/** Saves the single order form: 'closed' when the form closes after saving, 'errors' when it shows errors. */
export async function singleSave(page: Page): Promise<'closed' | 'errors'> {
  const form = singleForm(page);
  await form.locator('.action-button.confirm').click();
  const closed = expect(page.locator('#record-lab-results-section')).toBeHidden({ timeout: 30000 }).then(() => 'closed' as const);
  const err = form.locator('.errors-section').filter({ hasText: /\S/ }).waitFor({ timeout: 30000 }).then(() => 'errors' as const);
  const r = await Promise.race([closed, err]);
  await page.waitForLoadState('networkidle');
  return r;
}

export function orderRow(page: Page, orderUuid: string): Locator {
  return page.locator('#orders-table tbody tr', { has: page.locator('[data-order-uuid="' + orderUuid + '"]') }).first();
}

/**
 * Opens the Lab ID page for the patient as a lab tech would: from the "Enter results by Lab ID" patient-row action where
 * the site has it, else by its URL (Sierra Leone and Haiti do not configure the action).
 */
export async function openLabIdFromList(page: Page, inst: { hasLabIdAction: boolean }, patient: string) {
  await openOrderList(page, { patientGrouping: true, statuses: ['IN_FULFILLMENT', 'AWAITING_VALIDATION'] });
  await page.locator('#group-by-patient-btn').click();
  await filterOrderListOnPatient(page, patient);
  await expect(page.locator('#patients-table tbody tr').first()).toBeVisible();
  const action = page.locator('#patients-table .lab-ext-group-action[data-extension-id$=".lab.enterResults"][data-patient-uuid="' + patient + '"]');
  if (!inst.hasLabIdAction) {
    await expect(action).toHaveCount(0);
    await openLabId(page, patient, 'enter');
    return 'url';
  }
  await expect(action).toBeVisible();
  await expect(action).toHaveClass(/fa-clipboard-list/);
  await Promise.all([page.waitForURL(/labResultsByLabId\.page/), action.click()]);
  await waitLabIdLoaded(page);
  return 'action';
}
