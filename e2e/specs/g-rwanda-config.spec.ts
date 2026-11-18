import { test, expect } from '../lib/fixtures';
import { collectedOrders, enterViaRest, must } from '../lib/rest';
import { openOrderList, filterOrderListOnPatient } from '../lib/pages';
import { Page } from '@playwright/test';

async function patientRows(page: Page, patient: string, statuses: string[]) {
  await openOrderList(page, { patientGrouping: true, statuses });
  await page.locator('#group-by-patient-btn').click();
  await filterOrderListOnPatient(page, patient);
  return page.locator('#patients-table tbody tr', { has: page.locator('[data-patient-uuid="' + patient + '"]') }).first();
}

test.describe('G. Rwanda site configuration', () => {
  test.beforeEach(async ({ inst }) => {
    test.skip(inst.key !== 'rwanda', 'Rwanda configuration (rwandaemr-content / Butaro lab order list actions)');
  });

  test('G1 – Lab order list actions', async ({ inst, rest, as, shot }) => {
    const ordered = await rest.createPatient();
    await rest.placeOrders(ordered.uuid, [inst.numeric.uuid]);
    const collected = await collectedOrders(rest, [inst.numeric.uuid], 'G1');
    const awaiting = await collectedOrders(rest, [inst.numeric.uuid], 'G1A');
    await enterViaRest(rest, awaiting.specimen, [{ order: awaiting.orders[0].uuid, concept: inst.numeric.uuid, value: inst.numeric.normal }]);

    const page = await as(inst.users.validator);
    const enterAction = (p: string) => page.locator('#patients-table .lab-ext-group-action[data-extension-id="rwandaemr.lab.enterResults"][data-patient-uuid="' + p + '"]');
    const printAction = (p: string) => page.locator('#patients-table .lab-ext-group-action[data-extension-id="rwandaemr.lab.printResults"][data-patient-uuid="' + p + '"]');
    const validateAction = (p: string) => page.locator('#patients-table .validate-results-action[data-patient-uuid="' + p + '"]');

    let row = await patientRows(page, ordered.uuid, ['AWAITING_FULFILLMENT', 'IN_FULFILLMENT', 'AWAITING_VALIDATION']);
    await expect(row).toBeVisible();
    await expect(enterAction(ordered.uuid), 'no "Enter results by Lab ID" without a collected order').toHaveCount(0);
    await expect(printAction(ordered.uuid)).toHaveCount(1);
    await shot(page, 'patient-row-ordered-only');

    row = await patientRows(page, collected.patient.uuid, ['AWAITING_FULFILLMENT', 'IN_FULFILLMENT', 'AWAITING_VALIDATION']);
    if (inst.hasLabIdAction) {
      await expect(enterAction(collected.patient.uuid)).toHaveCount(1);
      await expect(enterAction(collected.patient.uuid)).toHaveClass(/fa-clipboard-list/);
      await expect(enterAction(collected.patient.uuid)).toHaveAttribute('title', 'Enter results by Lab ID');
    } else {
      await expect(enterAction(collected.patient.uuid), 'action removed from the configuration (LABID_ACTION=off)').toHaveCount(0);
    }
    await expect(printAction(collected.patient.uuid)).toHaveClass(/fa-print/);
    await expect(validateAction(collected.patient.uuid)).toHaveCount(0);
    await shot(page, 'patient-row-collected');

    // Payment badges on the order rows
    await page.locator('#group-by-order-btn').click();
    await page.waitForLoadState('networkidle');
    const badge = page.locator('#orders-table .lab-ext-order-badges .lab-ext-badge').first();
    await expect(badge).toBeVisible();
    await expect(badge).toHaveText(/^(Paid|Partially paid|Not paid|Not billed)$/);
    test.info().annotations.push({ type: 'payment badge', description: await badge.innerText() });
    await shot(page, 'order-row-payment-badge');

    // The built-in validate action: with validation on and the privilege
    row = await patientRows(page, awaiting.patient.uuid, ['AWAITING_VALIDATION']);
    await expect(validateAction(awaiting.patient.uuid)).toHaveCount(1);
    await shot(page, 'patient-row-awaiting-validation');
    await rest.setValidation(false);
    try {
      row = await patientRows(page, awaiting.patient.uuid, ['IN_FULFILLMENT']);
      await expect(row).toBeVisible();
      await expect(validateAction(awaiting.patient.uuid), 'no validate action with validation off').toHaveCount(0);
      await shot(page, 'patient-row-validation-off');
    } finally {
      await rest.setValidation(true);
    }
  });

  test('G2 – Results SMS waits for validation', async () => {
    test.skip(true, 'Not browser-testable here: the results SMS (rbcmessaging) needs an SMS gateway, which the test copy does not have');
  });

  test('G3 – Orders completed outside pihapps', async ({ inst, rest }) => {
    // rwandaemr completes such orders when the obs is saved (MarkOrderAsFulfilledEventHandler) and, failing that, in its
    // scheduled task every five minutes (MarkOrdersAsFulfilledOrExpiredTask)
    test.setTimeout(16 * 60_000);
    const patient = await rest.createPatient();
    const recordOutside = async (on: boolean) => {
      await rest.setValidation(on);
      const [o] = await rest.placeOrders(patient.uuid, [inst.numeric.uuid]);
      expect((await rest.labOrder(patient.uuid, o.uuid)).fulfillerStatus).toBeNull();
      // A result linked to the order by another path (a form or an integration), the order without fulfiller status
      must(await rest.call('POST', 'obs', { person: patient.uuid, concept: inst.numeric.uuid, value: 77, order: o.uuid, encounter: o.encounter,
        obsDatetime: new Date(Date.now() - 3600e3).toISOString().replace('Z', '+0000') }), 'post obs');
      const start = Date.now();
      await expect.poll(async () => (await rest.labOrder(patient.uuid, o.uuid)).fulfillerStatus, { timeout: 7 * 60_000, intervals: [5000] }).not.toBeNull();
      const seconds = Math.round((Date.now() - start) / 1000);
      test.info().annotations.push({ type: 'validation ' + (on ? 'on' : 'off'), description: 'fulfiller status set after ' + seconds + ' s' +
        (seconds > 10 ? ' (by the scheduled task: the obs save handler did not set it)' : '') });
      return (await rest.labOrder(patient.uuid, o.uuid)).fulfillerStatus;
    };
    try {
      expect(await recordOutside(true), 'validation on: left in progress for the lab to submit').toBe('IN_PROGRESS');
      expect(await recordOutside(false), 'validation off: completed as before').toBe('COMPLETED');
    } finally {
      await rest.setValidation(true);
    }
  });
});
