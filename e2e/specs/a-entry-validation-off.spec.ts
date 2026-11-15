import { test, expect, withValidation } from '../lib/fixtures';
import { collectedOrders, flatten } from '../lib/rest';
import {
  openOrderList, openSingleForm, singleStatus, singleStatusLabels, singleSave, singleForm, resultRow, fillResult,
  openLabId, lrOrder, setLrStatus, saveLabId, lrErrors,
} from '../lib/pages';
import { Locator, Page } from '@playwright/test';

/** Checks the flags of a numeric field for an abnormal, critical and invalid value, and that the row does not shift. */
async function checkFlags(page: Page, scope: Locator, inst: any, shot: (p: any, n: string) => Promise<void>, where: string) {
  const row = resultRow(scope, inst.numeric.uuid);
  const input = row.locator('input.result-numeric-input').first();
  const flag = row.locator('.result-flag').first();
  const units = row.locator('.result-units').first();
  const comment = row.locator('.comment-toggle').first();
  // Relative to the row, as the page may scroll
  const positions = async () => {
    const r = (await row.boundingBox())!, u = (await units.boundingBox())!, c = (await comment.boundingBox())!;
    return { units: Math.round(u.x - r.x), comment: Math.round(c.x - r.x), commentY: Math.round(c.y - r.y) };
  };

  await input.fill(inst.numeric.normal); await input.blur();
  const before = await positions();

  await input.fill(inst.numeric.abnormal); await input.blur();
  await expect(input).toHaveClass(/result-abnormal/);
  await expect(flag).toHaveClass(/result-flag-abnormal/);
  await expect(flag).toContainText('Abnormal value');
  expect(await positions(), 'units / Add comment do not move (abnormal)').toEqual(before);
  await shot(scope, where + '-abnormal');

  await input.fill(inst.numeric.critical); await input.blur();
  await expect(input).toHaveClass(/result-critical/);
  await expect(flag).toHaveClass(/result-flag-critical/);
  await expect(flag).toContainText('Critical value');
  expect(await positions(), 'units / Add comment do not move (critical)').toEqual(before);
  await shot(scope, where + '-critical');

  await input.fill(inst.numeric.invalid); await input.blur();
  await expect(input).toHaveClass(/result-invalid/);
  await expect(row.locator('.field-error.error-value')).toHaveText('Minimum allowed value: ' + inst.numeric.minimum);
  const after = await positions();
  expect(after.units, 'units do not move (invalid)').toEqual(before.units);
  expect(after.comment, 'Add comment does not move horizontally (invalid)').toEqual(before.comment);
  await shot(scope, where + '-invalid');
}

test.describe('A. Results entry, validation off', () => {
  withValidation(false);

  test('A1 – Single order form saves final results', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid], 'A1');
    const order = c.orders[0];
    const page = await as(inst.users.tech);
    await openOrderList(page, { labId: c.labId });
    await shot(page, 'order-list-collected');
    await openSingleForm(page, order.uuid);
    expect(await singleStatusLabels(page)).not.toContain('Submit for validation');
    await singleStatus(page, /Completed/);
    await fillResult(singleForm(page), inst.numeric.uuid, inst.numeric.normal);
    await shot(page, 'single-form-before-save');
    expect(await singleSave(page)).toBe('closed');
    await shot(page, 'order-list-after-save');

    const o = await rest.labOrder(c.patient.uuid, order.uuid);
    expect(o.fulfillerStatus).toBe('COMPLETED');
    expect(o.labResultValidation).toBeNull();
    expect(await rest.events(order.uuid)).toEqual([]);
    const results = flatten(await rest.encounterObs(c.specimen)).filter(x => x.obs.concept.uuid === inst.numeric.uuid);
    expect(results).toHaveLength(1);
    expect(results[0].obs.status).toBe('FINAL');
    expect(results[0].obs.display).toContain(inst.numeric.normal);

    await openOrderList(page, { statuses: ['COMPLETED_FULFILLMENT'], labId: c.labId });
    await expect(page.locator('#orders-table tbody tr').first()).toContainText(/Completed|Reported/);   // SL labels the completed status "Reported"
    await shot(page, 'order-list-completed');

    await page.goto('pihapps/labs/patientLabResults.page?patient=' + c.patient.uuid);
    await expect(page.locator('#results-table')).toContainText(inst.numeric.normal);
    await shot(page, 'patient-lab-results');
  });

  test('A2 – Selected status clicked again keeps the value', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid], 'A2');
    const page = await as(inst.users.tech);
    await openOrderList(page, { labId: c.labId });
    await openSingleForm(page, c.orders[0].uuid);
    await singleStatus(page, /Completed/);
    const input = resultRow(singleForm(page), inst.numeric.uuid).locator('.result-value-field').first();
    await input.fill(inst.numeric.normal);
    await singleStatus(page, /Completed/);
    await expect(input).toHaveValue(inst.numeric.normal);
    await shot(page, 'single-form-after-second-click');

    // The same on the Lab ID page
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, c.orders[0].orderNumber);
    await setLrStatus(block, 'COMPLETED');
    await block.locator('.result-value-field').first().fill(inst.numeric.normal);
    await setLrStatus(block, 'COMPLETED');
    await expect(block.locator('.result-value-field').first()).toHaveValue(inst.numeric.normal);
    await shot(page, 'labid-after-second-click');
  });

  test('A3 – Abnormal, critical and invalid values', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid], 'A3');
    const page = await as(inst.users.tech);

    await openOrderList(page, { labId: c.labId });
    await openSingleForm(page, c.orders[0].uuid);
    await singleStatus(page, /Completed/);
    const form = singleForm(page);
    await checkFlags(page, form.locator('.result-entry-section'), inst, shot, 'single-form');
    expect(await singleSave(page)).toBe('errors');
    await expect(form.locator('.errors-section')).toContainText('There are errors with one or more fields');
    await shot(page, 'single-form-save-refused');

    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, c.orders[0].orderNumber);
    await setLrStatus(block, 'COMPLETED');
    await checkFlags(page, block, inst, shot, 'labid');
    expect(await saveLabId(page)).toBe('errors');
    expect(await lrErrors(page)).toContain('There are errors with one or more fields');
    await shot(page, 'labid-save-refused');
    const o = await rest.labOrder(c.patient.uuid, c.orders[0].uuid);
    expect(o.fulfillerStatus, 'nothing saved').toBe('IN_PROGRESS');
  });

  test('A4 – Result fields stay aligned', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid, inst.numeric2.uuid], 'A4');
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    const a = lrOrder(page, c.orders[0].orderNumber), b = lrOrder(page, c.orders[1].orderNumber);
    await a.locator('input.result-numeric-input').fill(inst.numeric.abnormal); await a.locator('input.result-numeric-input').blur();
    await b.locator('input.result-numeric-input').fill(inst.numeric2.normal); await b.locator('input.result-numeric-input').blur();
    await expect(a.locator('.result-flag')).toContainText('Abnormal value');
    await shot(page.locator('.lr-specimen').first(), 'two-tests-one-abnormal');
    const x = async (l: Locator) => Math.round((await l.boundingBox())!.x);   // both rows measured at once: scrolling moves both
    expect(await x(a.locator('.result-units'))).toBe(await x(b.locator('.result-units')));
    expect(await x(a.locator('.comment-toggle'))).toBe(await x(b.locator('.comment-toggle')));
    for (const block of [a, b]) {
      const ys: number[] = [];
      for (const btn of await block.locator('.lr-status-cell .select-buttons button').all()) ys.push(Math.round((await btn.boundingBox())!.y));
      expect(new Set(ys).size, 'status buttons on one line').toBe(1);
    }
  });

  test('A5 – Re-saving keeps results in their panel group', async ({ inst, rest, as, shot }) => {
    const [m1, m2] = inst.panel.members;
    const c = await collectedOrders(rest, [inst.panel.uuid, inst.panelMemberAlone], 'A5');
    const [panelOrder, aloneOrder] = c.orders;
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    const pBlock = lrOrder(page, panelOrder.orderNumber), aBlock = lrOrder(page, aloneOrder.orderNumber);
    await fillResult(pBlock, m1.uuid, m1.value);
    await fillResult(pBlock, m2.uuid, m2.value);
    await fillResult(aBlock, inst.panelMemberAlone, inst.panelMemberAlone === m2.uuid ? '3.9' : '12');
    await setLrStatus(pBlock, 'COMPLETED');
    await setLrStatus(aBlock, 'COMPLETED');
    await shot(page, 'labid-panel-and-member-entered');
    expect(await saveLabId(page)).toBe('reloaded');

    const check = async () => {
      const tree = await rest.encounterObs(c.specimen);
      const groups = tree.filter(o => o.concept.uuid === inst.panel.uuid);
      expect(groups, 'one panel group').toHaveLength(1);
      expect(groups[0].order?.uuid).toBe(panelOrder.uuid);
      const members = groups[0].groupMembers.map((g: any) => g.concept.uuid);
      expect(members).toEqual(expect.arrayContaining([m1.uuid, m2.uuid]));
      const alone = tree.filter(o => o.concept.uuid === inst.panelMemberAlone && o.order?.uuid === aloneOrder.uuid);
      expect(alone, 'the separately ordered member is a top-level obs of its own order').toHaveLength(1);
      expect(groups[0].groupMembers.filter((g: any) => g.concept.uuid === inst.panelMemberAlone),
        'the panel group holds only its own result of that concept').toHaveLength(inst.panelMemberAlone === m2.uuid ? 1 : 0);
      return { group: groups[0], alone: alone[0] };
    };
    const first = await check();

    // Edit one member of the completed panel on the single order form
    await openOrderList(page, { statuses: ['COMPLETED_FULFILLMENT'], labId: c.labId });
    await openSingleForm(page, panelOrder.uuid);
    await fillResult(singleForm(page), m2.uuid, m2.value2);
    await shot(page, 'single-form-panel-edit');
    expect(await singleSave(page)).toBe('closed');

    const second = await check();
    const edited = second.group.groupMembers.find((g: any) => g.concept.uuid === m2.uuid);
    expect(edited.display).toContain(m2.value2);
    expect(edited.previousVersion, 'the edited result replaces the old one').not.toBeNull();
    const old = await rest.obs(edited.previousVersion.uuid);
    expect(old.voided).toBe(true);
    expect(second.alone.uuid, 'the other order\'s result is untouched').toBe(first.alone.uuid);
  });

  test('A6 – Mark all appears only with several tests', async ({ inst, rest, as, shot }) => {
    const two = await collectedOrders(rest, [inst.numeric.uuid, inst.numeric2.uuid], 'A6');
    const one = await collectedOrders(rest, [inst.numeric.uuid], 'A6');
    const page = await as(inst.users.tech);
    await openLabId(page, two.patient.uuid, 'enter');
    const markAll = page.locator('.lr-category-header button');
    await expect(markAll).toHaveCount(1);
    await expect(markAll).toHaveText('Mark all: Completed');
    await shot(page, 'labid-two-tests-mark-all');
    await markAll.click();
    for (const o of two.orders) await expect(lrOrder(page, o.orderNumber).locator('.lr-status-cell button.active')).toHaveAttribute('data-value', 'COMPLETED');
    await openLabId(page, one.patient.uuid, 'enter');
    await expect(page.locator('.lr-category-header button')).toHaveCount(0);
    await shot(page, 'labid-one-test-no-mark-all');
  });

  test('A7 – Error message from the server is shown as text', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid], 'A7');
    const page = await as(inst.users.tech);
    const htmlMessage = 'Result <b>rejected</b> & <i>not saved</i>';
    const pattern = '**/ws/rest/v1/encounterFulfillingOrders/**';
    await page.route(pattern, route => route.request().method() === 'POST'
      ? route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ error: { message: htmlMessage, code: 'x' } }) })
      : route.continue());

    // Single order form
    await openOrderList(page, { labId: c.labId });
    await openSingleForm(page, c.orders[0].uuid);
    await singleStatus(page, /Completed/);
    await fillResult(singleForm(page), inst.numeric.uuid, inst.numeric.normal);
    expect(await singleSave(page)).toBe('errors');
    const errors = singleForm(page).locator('.errors-section');
    await expect(errors).toHaveText(htmlMessage);
    await expect(errors.locator('b, i')).toHaveCount(0);
    await shot(page, 'single-form-json-error');

    // Lab ID page
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, c.orders[0].orderNumber);
    await block.locator('.result-value-field').first().fill(inst.numeric.normal);
    await setLrStatus(block, 'COMPLETED');
    expect(await saveLabId(page)).toBe('errors');
    expect(await lrErrors(page)).toContain(htmlMessage);
    await expect(page.locator('.lr-errors b, .lr-errors i')).toHaveCount(0);
    await shot(page, 'labid-json-error');

    // A server error page (html, not json) is not rendered either
    await page.unroute(pattern);
    await page.route(pattern, route => route.request().method() === 'POST'
      ? route.fulfill({ status: 500, contentType: 'text/html', body: '<html><body><h1>HTTP Status 500</h1></body></html>' })
      : route.continue());
    await page.locator('.lr-errors').first().evaluate(e => { e.innerHTML = ''; });
    expect(await saveLabId(page)).toBe('errors');
    const text = await lrErrors(page);
    expect(text).not.toContain('[object Object]');
    expect(text).not.toContain('<h1>');
    await expect(page.locator('.lr-errors h1')).toHaveCount(0);
    await shot(page, 'labid-html-error');
    expect((await rest.labOrder(c.patient.uuid, c.orders[0].uuid)).fulfillerStatus).toBe('IN_PROGRESS');
  });
});
