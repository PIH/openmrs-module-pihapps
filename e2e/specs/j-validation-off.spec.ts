import { test, expect, withValidation } from '../lib/fixtures';
import { collectedOrders, enterViaRest, must } from '../lib/rest';
import { openOrderList, openSingleForm, singleStatusLabels, openLabId, orderRow } from '../lib/pages';

test.describe('J. Validation off: nothing of validation shows', () => {
  withValidation(false);

  test('J1 – No validate action, filter, badge or label', async ({ inst, rest, as, shot }) => {
    // An order returned for correction while validation was on, and an order completed with validation off
    await rest.setValidation(true);
    const returned = await collectedOrders(rest, [inst.numeric.uuid], 'J1R');
    await enterViaRest(rest, returned.specimen, [{ order: returned.orders[0].uuid, concept: inst.numeric.uuid, value: '11.1' }]);
    must(await rest.decide('RETURN', [returned.orders[0].uuid], inst.users.admin, 'Re-check'), 'return');
    await rest.setValidation(false);
    const done = await collectedOrders(rest, [inst.numeric2.uuid], 'J1C');
    await enterViaRest(rest, done.specimen, [{ order: done.orders[0].uuid, concept: inst.numeric2.uuid, value: inst.numeric2.normal }]);
    expect((await rest.labOrder(done.patient.uuid, done.orders[0].uuid)).fulfillerStatus).toBe('COMPLETED');

    const page = await as(inst.users.validator);
    await openOrderList(page, { labId: returned.labId });
    await page.locator('#orderFulfillmentStatus-filter-toggle').click();
    await expect(page.locator('#orderFulfillmentStatus-option-AWAITING_VALIDATION')).toHaveCount(0);
    await expect(page.locator('#orderFulfillmentStatus-filter-menu')).not.toContainText('Awaiting validation');
    await shot(page, 'status-filter-options');
    await page.mouse.click(5, 5);
    const row = orderRow(page, returned.orders[0].uuid);
    await expect(row).toBeVisible();
    await expect(row.locator('.badge', { hasText: 'Returned for correction' })).toHaveCount(0);
    await expect(page.locator('.validate-results-action')).toHaveCount(0);
    await page.locator('#group-by-patient-btn').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.validate-results-action')).toHaveCount(0);
    await expect(page.locator('#patients-table .badge', { hasText: 'Returned for correction' })).toHaveCount(0);
    await shot(page, 'patient-rows-no-validation');
    await page.locator('#group-by-order-btn').click();
    await page.waitForLoadState('networkidle');
    await openSingleForm(page, returned.orders[0].uuid);
    const labels = await singleStatusLabels(page);
    expect(labels).toContain('Completed');
    expect(labels).not.toContain('Submit for validation');
    await shot(page, 'single-form-labels');

    await openLabId(page, returned.patient.uuid, 'validate');
    await expect(page.locator('.lr-empty')).toContainText('No lab results are awaiting validation for this patient.');
    await shot(page, 'validate-page-empty');
    await openLabId(page, returned.patient.uuid, 'enter');
    await expect(page.locator('.lr-hint')).toHaveCount(0);
    await expect(page.locator('.lr-status-cell button[data-value="COMPLETED"]').first()).toHaveText('Completed');

    // The patient lab results page shows all results, the preliminary one left from validation too
    for (const [c, value] of [[returned, '11.1'], [done, inst.numeric2.normal]] as const) {
      await page.goto('pihapps/labs/patientLabResults.page?patient=' + c.patient.uuid);
      await expect(page.locator('#results-table')).toContainText(value);
    }
    await shot(page, 'patient-results');
  });
});
