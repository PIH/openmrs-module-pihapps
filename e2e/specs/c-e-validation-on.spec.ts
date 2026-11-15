import { test, expect, withValidation } from '../lib/fixtures';
import { Page } from '@playwright/test';
import { Rest, Collected, collectedOrders, enterViaRest, validateViaRest, flatten, must } from '../lib/rest';
import { Instance, Creds } from '../lib/instances';
import {
  openLabId, lrOrder, setLrStatus, saveLabId, lrErrors, toasts, waitLabIdLoaded, recordToasts,
  openOrderList, filterOrderListOnPatient, openSingleForm, singleForm, singleStatus, singleStatusLabels, singleSave, fillResult, orderRow,
} from '../lib/pages';

/** New patient with the tests collected and their results submitted for validation by the lab tech (REST). */
async function submitted(rest: Rest, inst: Instance, tests: { uuid: string; value: string }[], prefix: string, user?: Creds): Promise<Collected> {
  const c = await collectedOrders(rest, tests.map(t => t.uuid), prefix);
  await enterViaRest(rest, c.specimen, tests.map((t, i) => ({ order: c.orders[i].uuid, concept: t.uuid, value: t.value })), 'COMPLETED', user);
  return c;
}

async function resultObs(rest: Rest, c: Collected, concept: string) {
  return flatten(await rest.encounterObs(c.specimen)).filter(x => x.obs.concept.uuid === concept).map(x => x.obs);
}

async function personName(rest: Rest, user: Creds): Promise<RegExp> {
  const u = (await rest.get('user?username=' + user.username + '&v=custom:(username,systemId,person:(display))')).results.find((x: any) => (x.username || x.systemId) === user.username);
  const names = [user.username, ...(u?.person?.display ? u.person.display.split(/[ ,]+/).filter((s: string) => s.length > 2) : [])];
  return new RegExp(names.map(n => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|'));
}

/** Opens the validate page from the lab order list's patient row (grouped by patient). */
async function openValidateFromPatientRow(page: Page, patient: string) {
  await openOrderList(page, { patientGrouping: true, statuses: ['AWAITING_VALIDATION'] });
  await page.locator('#group-by-patient-btn').click();
  await filterOrderListOnPatient(page, patient);
  const action = page.locator('#patients-table .validate-results-action[data-patient-uuid="' + patient + '"]');
  await expect(action).toBeVisible();
  await Promise.all([page.waitForURL(/labResultsByLabId\.page.*mode=validate/), action.click()]);
  await waitLabIdLoaded(page);
}

test.describe('C–E. Validation on', () => {
  withValidation(true);

  // ---------------- C. Submitting ----------------

  test('C1 – Submit for validation (Lab ID page)', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid], 'C1');
    const order = c.orders[0];
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    await expect(page.locator('.lr-hint')).toContainText('Submitted results are shown there once another user validates them');
    const block = lrOrder(page, order.orderNumber);
    await expect(block.locator('.lr-status-cell button[data-value="COMPLETED"]')).toHaveText('Submit for validation');
    await block.locator('.result-value-field').first().fill(inst.numeric.normal);
    await setLrStatus(block, 'COMPLETED');
    await shot(page, 'labid-before-submit');
    expect(await saveLabId(page)).toBe('reloaded');
    await shot(page, 'labid-after-submit');

    const o = await rest.labOrder(c.patient.uuid, order.uuid);
    expect(o.fulfillerStatus).toBe('IN_PROGRESS');
    expect(o.labResultValidation?.eventType).toBe('RESULTS_SUBMITTED');
    const obs = await resultObs(rest, c, inst.numeric.uuid);
    expect(obs.map(x => x.status)).toEqual(['PRELIMINARY']);
    const events = await rest.events(order.uuid);
    expect(events.map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED']);
    expect(events[0].creator).toMatch(await personName(rest, inst.users.tech));

    await openOrderList(page, { labId: c.labId });
    await expect(orderRow(page, order.uuid)).toContainText('Awaiting validation');
    await shot(page, 'order-list-awaiting-validation');
  });

  test('C1 – Submit for validation (single order form)', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid], 'C1S');
    const order = c.orders[0];
    const page = await as(inst.users.tech);
    await openOrderList(page, { labId: c.labId });
    await openSingleForm(page, order.uuid);
    expect(await singleStatusLabels(page)).toContain('Submit for validation');
    expect(await singleStatusLabels(page)).not.toContain('Completed');
    await singleStatus(page, 'Submit for validation');
    await fillResult(singleForm(page), inst.numeric.uuid, inst.numeric.normal);
    await shot(page, 'single-form-before-submit');
    expect(await singleSave(page)).toBe('closed');
    await expect(orderRow(page, order.uuid)).toContainText('Awaiting validation');
    await shot(page, 'order-list-after-submit');
    const o = await rest.labOrder(c.patient.uuid, order.uuid);
    expect(o.fulfillerStatus).toBe('IN_PROGRESS');
    expect(o.labResultValidation?.eventType).toBe('RESULTS_SUBMITTED');
    expect((await resultObs(rest, c, inst.numeric.uuid)).map(x => x.status)).toEqual(['PRELIMINARY']);
  });

  test('C2 – Clinicians do not see results awaiting validation', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: '12.3' }], 'C2');
    const page = await as(inst.users.clinician);
    await page.goto('pihapps/labs/patientLabResults.page?patient=' + c.patient.uuid);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#results-table')).toBeVisible();
    await expect(page.locator('#results-table')).not.toContainText('12.3');
    await shot(page, 'patient-results-without-submitted');
    // What the results page and the trends chart ask for
    const shown = await rest.labResults(c.patient.uuid, ['FINAL', 'AMENDED']);
    expect(shown.filter(r => r.concept.uuid === inst.numeric.uuid)).toEqual([]);
    const trends = await rest.get('pihapps/labResults?patient=' + c.patient.uuid + '&labTest=' + inst.numeric.uuid + '&status=FINAL&status=AMENDED&v=custom:(uuid)');
    expect(trends.results).toEqual([]);
  });

  test('C3 – Correcting a result awaiting validation', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: '12.1' }], 'C3');
    const order = c.orders[0];
    const page = await as(inst.users.tech);
    await openOrderList(page, { labId: c.labId });
    await openSingleForm(page, order.uuid);
    const labels = await singleStatusLabels(page);
    expect(labels).toContain('Submit for validation');
    expect(labels).not.toContain('In progress');
    await expect(singleForm(page).locator('.fulfiller-status button.active')).toHaveText('Submit for validation');
    await fillResult(singleForm(page), inst.numeric.uuid, '12.4');
    await shot(page, 'single-form-correcting');
    expect(await singleSave(page)).toBe('closed');
    const o = await rest.labOrder(c.patient.uuid, order.uuid);
    expect(o.fulfillerStatus).toBe('IN_PROGRESS');
    expect(o.labResultValidation?.eventType).toBe('RESULTS_SUBMITTED');
    const obs = await resultObs(rest, c, inst.numeric.uuid);
    expect(obs).toHaveLength(1);
    expect(obs[0].display).toContain('12.4');
    expect(obs[0].status).toBe('PRELIMINARY');
    expect((await rest.events(order.uuid)).map(e => e.eventType), 'no second RESULTS_SUBMITTED').toEqual(['RESULTS_SUBMITTED']);

    // The same on the Lab ID page: opens as Submit for validation, without In progress
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, order.orderNumber);
    await expect(block.locator('.lr-status-cell button.active')).toHaveAttribute('data-value', 'COMPLETED');
    await expect(block.locator('.lr-status-cell button[data-value="IN_PROGRESS"]')).toHaveCount(0);
    await shot(page, 'labid-awaiting-opens-as-submitted');
  });

  // ---------------- D. Validating ----------------

  test('D1 – Validate all', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }, { uuid: inst.numeric2.uuid, value: inst.numeric2.normal }], 'D1');
    const page = await as(inst.users.validator);
    await openValidateFromPatientRow(page, c.patient.uuid);
    await expect(page.locator('.lr-decision-summary')).toHaveText('To validate: 2 · To return: 0');
    for (const o of c.orders) await expect(lrOrder(page, o.orderNumber).locator('button[data-value="VALIDATE"]')).toHaveClass(/active/);
    await shot(page, 'validate-page');
    expect(await saveLabId(page)).toBe('reloaded');
    expect(await toasts(page)).toContain('successMessage: Validated: 2 · Returned for correction: 0');
    await shot(page, 'after-validate');

    for (const o of c.orders) {
      expect((await rest.labOrder(c.patient.uuid, o.uuid)).fulfillerStatus).toBe('COMPLETED');
      const ev = await rest.events(o.uuid);
      expect(ev.map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED', 'RESULTS_VALIDATED']);
      expect(ev[1].creator).toMatch(await personName(rest, inst.users.validator));
    }
    expect((await resultObs(rest, c, inst.numeric.uuid)).map(x => x.status)).toEqual(['FINAL']);
    expect((await resultObs(rest, c, inst.numeric2.uuid)).map(x => x.status)).toEqual(['FINAL']);

    const clin = await as(inst.users.clinician);
    await clin.goto('pihapps/labs/patientLabResults.page?patient=' + c.patient.uuid);
    await expect(clin.locator('#results-table')).toContainText(inst.numeric.normal);
    await expect(clin.locator('#results-table')).toContainText(inst.numeric2.normal);
    await shot(clin, 'clinician-sees-validated');
  });

  test('D2 – Validate one, return the other with its own reason', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }, { uuid: inst.numeric2.uuid, value: inst.numeric2.normal }], 'D2');
    const [o1, o2] = c.orders;
    const page = await as(inst.users.validator);
    // From the order's validate icon, which opens that order's Lab ID
    await openOrderList(page, { labId: c.labId });
    await Promise.all([page.waitForURL(/mode=validate.*labId=|labId=.*mode=validate/),
      page.locator('#orders-table .validate-results-action[data-lab-id="' + c.labId + '"]').first().click()]);
    await waitLabIdLoaded(page);
    const b2 = lrOrder(page, o2.orderNumber);
    await b2.locator('button[data-value="RETURN"]').click();
    await expect(page.locator('.lr-decision-summary')).toHaveText('To validate: 1 · To return: 1');
    expect(await saveLabId(page)).toBe('errors');
    await expect(b2.locator('.lr-reason-required')).toBeVisible();
    await expect(b2.locator('.lr-reason-required')).toHaveText('Reason is required');
    expect(await lrErrors(page)).toContain('Reason is required');
    await shot(page, 'return-without-reason');
    expect((await rest.events(o1.uuid)).map(e => e.eventType), 'nothing saved').toEqual(['RESULTS_SUBMITTED']);
    await b2.locator('textarea').fill('Implausible value, please repeat');
    expect(await lrErrors(page), 'the top message clears once a reason is typed').toBe('');
    await shot(page, 'return-with-reason');
    expect(await saveLabId(page)).toBe('reloaded');
    expect(await toasts(page)).toContain('successMessage: Validated: 1 · Returned for correction: 1');

    expect((await rest.labOrder(c.patient.uuid, o1.uuid)).fulfillerStatus).toBe('COMPLETED');
    const s2 = await rest.labOrder(c.patient.uuid, o2.uuid);
    expect(s2.fulfillerStatus).toBe('IN_PROGRESS');
    expect(s2.labResultValidation.eventType).toBe('RESULTS_RETURNED');
    expect(s2.labResultValidation.comment).toBe('Implausible value, please repeat');
    expect((await resultObs(rest, c, inst.numeric2.uuid)).map(x => x.status)).toEqual(['PRELIMINARY']);
    expect((await resultObs(rest, c, inst.numeric.uuid)).map(x => x.status)).toEqual(['FINAL']);
  });

  test('D3 – Returned results go back to the lab', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: '9.9' }], 'D3');
    const order = c.orders[0];
    must(await rest.decide('RETURN', [order.uuid], inst.users.validator, 'Please re-check'), 'return');
    const page = await as(inst.users.tech);
    await openOrderList(page, { labId: c.labId });
    await expect(orderRow(page, order.uuid).locator('.badge', { hasText: 'Returned for correction' })).toBeVisible();
    await shot(page, 'order-list-returned-badge');
    await openSingleForm(page, order.uuid);
    await expect(singleForm(page).locator('.returned-for-correction-section')).toBeVisible();
    await expect(singleForm(page).locator('.returned-for-correction-comment')).toHaveText('Please re-check');
    await shot(page, 'single-form-returned');

    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, order.orderNumber);
    await expect(block.locator('.lr-note')).toContainText('Returned for correction: Please re-check');
    await expect(block.locator('.lr-status-cell button.active')).toHaveAttribute('data-value', 'COMPLETED');
    await expect(block.locator('.lr-status-cell button[data-value="IN_PROGRESS"]'), 'In progress still offered').toHaveCount(1);
    await shot(page, 'labid-returned');
    await block.locator('.result-value-field').first().fill('10.4');
    expect(await saveLabId(page)).toBe('reloaded');
    const o = await rest.labOrder(c.patient.uuid, order.uuid);
    expect(o.labResultValidation.eventType).toBe('RESULTS_SUBMITTED');
    expect((await rest.events(order.uuid)).map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED', 'RESULTS_RETURNED', 'RESULTS_SUBMITTED']);
  });

  test('D4 – Abnormal flags on the validate page', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.abnormal }], 'D4');
    const c2 = await collectedOrders(rest, [inst.numeric.uuid], 'D4C');
    await enterViaRest(rest, c2.specimen, [{ order: c2.orders[0].uuid, concept: inst.numeric.uuid, value: inst.numeric.critical }]);
    const page = await as(inst.users.validator);
    await openLabId(page, c.patient.uuid, 'validate');
    const b = lrOrder(page, c.orders[0].orderNumber);
    await expect(b.locator('.lr-value.lr-abnormal')).toBeVisible();
    await expect(b.locator('.result-flag.result-flag-abnormal')).toContainText('Abnormal value');
    await shot(page, 'validate-abnormal');
    await openLabId(page, c2.patient.uuid, 'validate');
    const b2 = lrOrder(page, c2.orders[0].orderNumber);
    await expect(b2.locator('.lr-value.lr-critical')).toBeVisible();
    await expect(b2.locator('.result-flag.result-flag-critical')).toContainText('Critical value');
    await shot(page, 'validate-critical');
  });

  test('D5 – Partial failure of a save', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }, { uuid: inst.numeric2.uuid, value: inst.numeric2.normal }], 'D5');
    const [o1, o2] = c.orders;
    const page = await as(inst.users.validator);
    const sent: any[] = [];
    page.on('request', r => { if (r.method() === 'POST' && /labResultValidation/.test(r.url())) sent.push(r.postDataJSON()); });
    await page.route('**/ws/rest/v1/pihapps/labResultValidation', route => (route.request().method() === 'POST' && route.request().postDataJSON().action === 'RETURN')
      ? route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ success: false, message: 'Simulated failure' }) })
      : route.continue());
    await openLabId(page, c.patient.uuid, 'validate');
    const b1 = lrOrder(page, o1.orderNumber), b2 = lrOrder(page, o2.orderNumber);
    await b2.locator('button[data-value="RETURN"]').click();
    await b2.locator('textarea').fill('Wrong tube');
    expect(await saveLabId(page)).toBe('errors');
    await expect(b1).toHaveClass(/lr-saved/);
    await expect(b1.locator('.lr-done-note')).toHaveText('Validated');
    expect(await lrErrors(page)).toContain('Already recorded: ');
    await shot(page, 'return-failed');
    expect((await rest.labOrder(c.patient.uuid, o1.uuid)).fulfillerStatus).toBe('COMPLETED');

    await page.unroute('**/ws/rest/v1/pihapps/labResultValidation');
    sent.length = 0;
    expect(await saveLabId(page)).toBe('reloaded');
    expect(sent.map(s => s.action + ':' + s.orders.join(',')), 'only the return is sent again').toEqual(['RETURN:' + o2.uuid]);
    expect((await rest.labOrder(c.patient.uuid, o2.uuid)).labResultValidation.eventType).toBe('RESULTS_RETURNED');
    expect((await rest.events(o1.uuid)).filter(e => e.eventType === 'RESULTS_VALIDATED')).toHaveLength(1);
  });

  test('D6 – Mark all: Validate', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }, { uuid: inst.numeric2.uuid, value: inst.numeric2.normal }], 'D6');
    const page = await as(inst.users.validator);
    await openLabId(page, c.patient.uuid, 'validate');
    const markAll = page.locator('.lr-category-header button');
    await expect(markAll).toHaveText('Mark all: Validate');
    for (const o of c.orders) await lrOrder(page, o.orderNumber).locator('button[data-value="RETURN"]').click();
    await expect(page.locator('.lr-decision-summary')).toHaveText('To validate: 0 · To return: 2');
    await markAll.click();
    for (const o of c.orders) await expect(lrOrder(page, o.orderNumber).locator('button[data-value="VALIDATE"]')).toHaveClass(/active/);
    await expect(page.locator('.lr-decision-summary')).toHaveText('To validate: 2 · To return: 0');
    await shot(page, 'mark-all-validate');
    const one = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }], 'D6B');
    await openLabId(page, one.patient.uuid, 'validate');
    await expect(page.locator('.lr-category-header button')).toHaveCount(0);
    await shot(page, 'one-test-no-mark-all');
  });

  // ---------------- E. Separation of duties and state changes ----------------

  test('E1 – Whoever entered results cannot validate them', async ({ inst, rest, as, shot }) => {
    // A user with the validate privilege who enters results (Rwanda's lab tech has it; on Sierra Leone the validator)
    const enterer = inst.techHasValidatePrivilege ? inst.users.tech : inst.users.validator;
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }], 'E1', enterer);
    const order = c.orders[0];
    const page = await as(enterer);
    await openLabId(page, c.patient.uuid, 'validate');
    const b = lrOrder(page, order.orderNumber);
    await expect(b.locator('.lr-cannot-decide')).toHaveText('You entered or changed these results, so another user must validate them.');
    await expect(b.locator('button[data-value="VALIDATE"]')).toHaveCount(0);
    await shot(page, 'entered-by-me');
    const info = await rest.validationInfo([order.uuid], enterer);
    expect(info[order.uuid].canValidate).toBe(false);
    const r = await rest.decide('VALIDATE', [order.uuid], enterer, undefined, { [order.uuid]: info[order.uuid].results });
    expect(r.status).toBe(400);
    expect(r.body.message).toBe('You entered or changed the results of order ' + order.orderNumber + ', so another user must validate them');
    // Translated: the same request in a French session, where the site allows French
    const allowed: string[] = (await rest.get('session?v=custom:(allowedLocales)', enterer)).allowedLocales ?? [];
    if (allowed.includes('fr')) {
      must(await rest.call('POST', 'session', { locale: 'fr' }, enterer), 'French session');
      const rFr = await rest.decide('VALIDATE', [order.uuid], enterer, undefined, { [order.uuid]: info[order.uuid].results });
      await rest.call('POST', 'session', { locale: 'en' }, enterer);
      expect(rFr.status).toBe(400);
      expect(rFr.body.message).toContain(order.orderNumber);
      expect(rFr.body.message, 'message translated in a French session').not.toBe(r.body.message);
      test.info().annotations.push({ type: 'French message', description: rFr.body.message });
    } else {
      test.info().annotations.push({ type: 'translation not checked', description: 'the site allows only ' + allowed.join(', ') });
    }
  });

  test('E2 – Whoever submitted results cannot validate them', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid], 'E2');
    const order = c.orders[0];
    await enterViaRest(rest, c.specimen, [{ order: order.uuid, concept: inst.numeric.uuid, value: inst.numeric.normal }], 'IN_PROGRESS', inst.users.tech);
    const page = await as(inst.users.validator);
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, order.orderNumber);
    await expect(block.locator('.result-value-field').first()).toHaveValue(inst.numeric.normal);
    await setLrStatus(block, 'COMPLETED');
    await shot(page, 'validator-submits-unchanged');
    expect(await saveLabId(page)).toBe('reloaded');
    const ev = await rest.events(order.uuid);
    expect(ev.map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED']);
    expect(ev[0].creator).toMatch(await personName(rest, inst.users.validator));
    await openLabId(page, c.patient.uuid, 'validate');
    await expect(lrOrder(page, order.orderNumber).locator('.lr-cannot-decide')).toBeVisible();
    await shot(page, 'validator-cannot-validate');
    expect((await rest.validationInfo([order.uuid], inst.users.validator))[order.uuid].canValidate).toBe(false);
  });

  test('E3 – Editing the specimen collection does not block validating', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }], 'E3');
    const order = c.orders[0];
    const cfg = await rest.get('pihapps/config?v=custom:(labOrderConfig:(specimenReceivedDateQuestion:(uuid)))');
    const received = cfg.labOrderConfig.specimenReceivedDateQuestion.uuid;
    // The validator records the specimen's received date, as the specimen collection form saves it
    must(await rest.call('POST', 'encounterFulfillingOrders/' + c.specimen, {
      encounter: { uuid: c.specimen, obs: [{ concept: received, value: new Date(Date.now() - 6.5 * 3600e3).toISOString().replace('Z', '+0000'), formNamespaceAndPath: 'pihapps^specimenCollection/received_date' }] },
      orders: [order.uuid],
    }, inst.users.validator), 'edit specimen collection');
    expect((await rest.labOrder(c.patient.uuid, order.uuid)).labResultValidation.eventType).toBe('RESULTS_SUBMITTED');
    expect((await rest.validationInfo([order.uuid], inst.users.validator))[order.uuid].canValidate).toBe(true);
    const page = await as(inst.users.validator);
    await openLabId(page, c.patient.uuid, 'validate');
    await expect(lrOrder(page, order.orderNumber).locator('button[data-value="VALIDATE"]')).toBeVisible();
    await shot(page, 'validate-after-specimen-edit');
    expect(await saveLabId(page)).toBe('reloaded');
    expect((await rest.labOrder(c.patient.uuid, order.uuid)).fulfillerStatus).toBe('COMPLETED');
  });

  test('E4 – Not performed withdraws the submission', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }], 'E4');
    const order = c.orders[0];
    const page = await as(inst.users.tech);
    await openOrderList(page, { labId: c.labId });
    await openSingleForm(page, order.uuid);
    await singleStatus(page, 'Not performed');
    const reason = singleForm(page).locator('.reason-not-performed select').first();
    const firstReason = await reason.locator('option').nth(1).getAttribute('value');
    await reason.selectOption(firstReason!);
    await shot(page, 'mark-not-performed');
    expect(await singleSave(page)).toBe('closed');
    let o = await rest.labOrder(c.patient.uuid, order.uuid);
    expect(o.fulfillerStatus).toBe('EXCEPTION');
    expect((await rest.events(order.uuid)).map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED', 'RESULTS_WITHDRAWN']);

    // Back to In progress on the single order form
    await openOrderList(page, { labId: c.labId, statuses: ['UNABLE_TO_COMPLETE_FULFILLMENT'] });
    await openSingleForm(page, order.uuid);
    await singleStatus(page, 'In progress');
    await shot(page, 'back-in-progress');
    expect(await singleSave(page)).toBe('closed');
    o = await rest.labOrder(c.patient.uuid, order.uuid);
    expect(o.fulfillerStatus).toBe('IN_PROGRESS');
    expect(o.labResultValidation?.eventType, 'not awaiting validation').not.toBe('RESULTS_SUBMITTED');
    const info = await rest.validationInfo([order.uuid], inst.users.validator);
    expect(info[order.uuid].canValidate).toBe(false);
    const r = await rest.decide('VALIDATE', [order.uuid], inst.users.validator, undefined, { [order.uuid]: info[order.uuid].results });
    expect(r.status).toBe(400);
    expect(r.body.message).toBe('The results of order ' + order.orderNumber + ' are not awaiting validation');
    test.info().annotations.push({ type: 'not covered', description: 'The "revert to ordered" variant is not reachable from these pages in this build; not automated' });
  });

  test('E5 – Results changed while the validator has the page open', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }], 'E5');
    const order = c.orders[0];
    const val = await as(inst.users.validator);
    await openLabId(val, c.patient.uuid, 'validate');
    await shot(val, 'validator-page-open');
    // The lab tech corrects the value on the single order form
    const tech = await as(inst.users.tech);
    await openOrderList(tech, { labId: c.labId });
    await openSingleForm(tech, order.uuid);
    await fillResult(singleForm(tech), inst.numeric.uuid, inst.numeric2.normal === inst.numeric.normal ? '14' : String(Number(inst.numeric.normal) + 1));
    expect(await singleSave(tech)).toBe('closed');

    expect(await saveLabId(val)).toBe('errors');
    expect(await lrErrors(val)).toContain('The results of order ' + order.orderNumber + ' have changed since you opened them. Reload the page to review them again.');
    await shot(val, 'validate-refused-changed');
    expect((await rest.events(order.uuid)).map(e => e.eventType), 'nothing recorded').toEqual(['RESULTS_SUBMITTED']);
    await openLabId(val, c.patient.uuid, 'validate');
    expect(await saveLabId(val)).toBe('reloaded');
    expect((await rest.labOrder(c.patient.uuid, order.uuid)).fulfillerStatus).toBe('COMPLETED');
  });

  test('E6 – Editing a validated result records an amendment', async ({ inst, rest, as, shot }) => {
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }], 'E6');
    const order = c.orders[0];
    await validateViaRest(rest, [order.uuid]);
    const before = (await resultObs(rest, c, inst.numeric.uuid))[0];
    expect(before.status).toBe('FINAL');
    const page = await as(inst.users.tech);
    await openOrderList(page, { labId: c.labId, statuses: ['COMPLETED_FULFILLMENT'] });
    await openSingleForm(page, order.uuid);
    const labels = await singleStatusLabels(page);
    expect(labels).not.toContain('In progress');
    test.info().annotations.push({ type: 'status choices on a validated order', description: labels.join(', ') });
    const amended = String(Number(inst.numeric.normal) + 1);
    await fillResult(singleForm(page), inst.numeric.uuid, amended);
    await shot(page, 'single-form-amend');
    expect(await singleSave(page)).toBe('closed');
    expect((await rest.labOrder(c.patient.uuid, order.uuid)).fulfillerStatus).toBe('COMPLETED');
    const after = await resultObs(rest, c, inst.numeric.uuid);
    expect(after).toHaveLength(1);
    expect(after[0].status).toBe('AMENDED');
    expect(after[0].display).toContain(amended);
    expect(after[0].previousVersion?.uuid).toBe(before.uuid);
    expect((await rest.events(order.uuid)).map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED', 'RESULTS_VALIDATED', 'RESULTS_AMENDED']);

    const clin = await as(inst.users.clinician);
    await clin.goto('pihapps/labs/patientLabResults.page?patient=' + c.patient.uuid);
    await expect(clin.locator('#results-table')).toContainText(amended);
    await shot(clin, 'clinician-sees-amended');

    // Saving again without changes records nothing
    await openOrderList(page, { labId: c.labId, statuses: ['COMPLETED_FULFILLMENT'] });
    await openSingleForm(page, order.uuid);
    expect(await singleSave(page)).toBe('closed');
    expect((await rest.events(order.uuid)).map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED', 'RESULTS_VALIDATED', 'RESULTS_AMENDED']);
    expect((await resultObs(rest, c, inst.numeric.uuid))[0].uuid).toBe(after[0].uuid);
  });

  test('E7 – Validation events need the validate privilege', async ({ inst, rest, as, shot }) => {
    test.skip(!inst.users.noPriv, 'Environment: both Rwanda test users are System Developer, so there is no lab tech without the privilege');
    const noPriv = inst.users.noPriv!;
    const c = await submitted(rest, inst, [{ uuid: inst.numeric.uuid, value: inst.numeric.normal }], 'E7');
    const order = c.orders[0];
    const page = await as(noPriv);
    await openOrderList(page, { labId: c.labId });
    await expect(orderRow(page, order.uuid)).toContainText('Awaiting validation');
    await expect(page.locator('.validate-results-action')).toHaveCount(0);
    await page.locator('#group-by-patient-btn').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.validate-results-action')).toHaveCount(0);
    await shot(page, 'no-validate-action');
    const info = await rest.validationInfo([order.uuid]);
    for (const action of ['VALIDATE', 'RETURN'] as const) {
      const r = await rest.decide(action, [order.uuid], noPriv, 'x', { [order.uuid]: info[order.uuid].results });
      expect(r.status, action + ' without the privilege').toBe(403);
    }
  });
});
