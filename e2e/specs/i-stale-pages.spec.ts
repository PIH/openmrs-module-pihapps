import { test, expect, withValidation } from '../lib/fixtures';
import { collectedOrders, enterViaRest, flatten } from '../lib/rest';
import { openLabId, lrOrder, saveLabId, lrErrors } from '../lib/pages';

// One request after the other (simultaneous requests are deliberately not guarded against)

test.describe('I. Stale pages', () => {
  withValidation(true);

  const setup = async (rest: any, inst: any, prefix: string) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid], prefix);
    await enterViaRest(rest, c.specimen, [{ order: c.orders[0].uuid, concept: inst.numeric.uuid, value: inst.numeric.normal }]);
    return c;
  };

  test('I1 – Two validators, same order', async ({ inst, rest, as, shot }) => {
    test.skip(!inst.users.validator2, 'Environment: only one user (admin) has the validate privilege on this instance');
    const c = await setup(rest, inst, 'I1');
    const order = c.orders[0];
    const a = await as(inst.users.validator), b = await as(inst.users.validator2!);
    await openLabId(a, c.patient.uuid, 'validate');
    await openLabId(b, c.patient.uuid, 'validate');
    expect(await saveLabId(b)).toBe('reloaded');
    await shot(b, 'first-validator-validated');
    expect(await saveLabId(a)).toBe('errors');
    expect(await lrErrors(a)).toContain('The results of order ' + order.orderNumber + ' are not awaiting validation');
    await shot(a, 'second-validator-refused');
    expect((await rest.events(order.uuid)).filter(e => e.eventType === 'RESULTS_VALIDATED')).toHaveLength(1);
  });

  test('I2 – Validate after another validator returned', async ({ inst, rest, as, shot }) => {
    test.skip(!inst.users.validator2, 'Environment: only one user (admin) has the validate privilege on this instance');
    const c = await setup(rest, inst, 'I2');
    const order = c.orders[0];
    const a = await as(inst.users.validator), b = await as(inst.users.validator2!);
    await openLabId(a, c.patient.uuid, 'validate');
    await openLabId(b, c.patient.uuid, 'validate');
    const block = lrOrder(b, order.orderNumber);
    await block.locator('button[data-value="RETURN"]').click();
    await block.locator('textarea').fill('Haemolysed sample');
    expect(await saveLabId(b)).toBe('reloaded');
    expect(await saveLabId(a)).toBe('errors');
    expect(await lrErrors(a)).toContain('The results of order ' + order.orderNumber + ' are not awaiting validation');
    await shot(a, 'validate-after-return-refused');
    expect((await rest.events(order.uuid)).map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED', 'RESULTS_RETURNED']);
  });

  test('I3 – Lab edits after the validator opened the page', async ({ inst, rest, as, shot }) => {
    const c = await setup(rest, inst, 'I3');
    const order = c.orders[0];
    const val = await as(inst.users.validator);
    await openLabId(val, c.patient.uuid, 'validate');
    const tech = await as(inst.users.tech);
    await openLabId(tech, c.patient.uuid, 'enter');
    const block = lrOrder(tech, order.orderNumber);
    await block.locator('.result-value-field').first().fill(String(Number(inst.numeric.normal) + 2));
    expect(await saveLabId(tech)).toBe('reloaded');
    await shot(tech, 'lab-saved-change');
    expect(await saveLabId(val)).toBe('errors');
    expect(await lrErrors(val)).toContain('have changed since you opened them. Reload the page to review them again.');
    await shot(val, 'validate-refused');
    expect((await rest.events(order.uuid)).filter(e => e.eventType === 'RESULTS_VALIDATED')).toHaveLength(0);
    await openLabId(val, c.patient.uuid, 'validate');
    expect(await saveLabId(val)).toBe('reloaded');
    expect((await rest.labOrder(c.patient.uuid, order.uuid)).fulfillerStatus).toBe('COMPLETED');
    const obs = flatten(await rest.encounterObs(c.specimen)).filter(x => x.obs.concept.uuid === inst.numeric.uuid);
    expect(obs.map(x => x.obs.status)).toEqual(['FINAL']);
  });
});
