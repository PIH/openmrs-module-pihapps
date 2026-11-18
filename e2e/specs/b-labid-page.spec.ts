import { test, expect } from '../lib/fixtures';
import { collectedOrders, flatten, uniqueLabId } from '../lib/rest';
import { openLabId, openLabIdFromList, lrOrder, setLrStatus, saveLabId, lrErrors, fillResult, waitLabIdLoaded } from '../lib/pages';

// Validation "either": each instance runs at its own setting (SL and Haiti off, Rwanda on)

test.describe('B. Lab ID page', () => {

  test('B1 – One Lab ID at a time, with a choice row', async ({ inst, rest, as, shot }) => {
    const patient = await rest.createPatient();
    const [a1, a2] = await rest.placeOrders(patient.uuid, [inst.numeric.uuid, inst.numeric2.uuid], { hours: 10 });
    const labA = uniqueLabId('B1A');
    await rest.collect(patient.uuid, labA, [a1.uuid, a2.uuid], undefined, 9);
    const [b1] = await rest.placeOrders(patient.uuid, [inst.numeric.uuid], { hours: 8 });
    const labB = uniqueLabId('B1B');
    await rest.collect(patient.uuid, labB, [b1.uuid], undefined, 7);

    const page = await as(inst.users.tech);
    const how = await openLabIdFromList(page, inst, patient.uuid);
    test.info().annotations.push({ type: 'opened', description: how === 'action' ? 'from the "Enter results by Lab ID" patient action' : 'by URL (no patient action on this site)' });
    const choices = page.locator('.lr-group-choice');
    await expect(choices).toHaveCount(2);
    await expect(choices.nth(0)).toContainText(labA);
    await expect(choices.nth(1)).toContainText(labB);
    await expect(choices.nth(0)).toHaveClass(/lr-current/);
    await expect(lrOrder(page, a1.orderNumber)).toBeVisible();
    await expect(lrOrder(page, b1.orderNumber)).toHaveCount(0);
    await shot(page, 'two-lab-ids-oldest-open');

    await Promise.all([page.waitForURL(new RegExp('labId=' + labB)), choices.nth(1).click()]);
    await waitLabIdLoaded(page);
    await expect(page.locator('.lr-group-choice.lr-current')).toContainText(labB);
    await expect(lrOrder(page, b1.orderNumber)).toBeVisible();
    await expect(lrOrder(page, a1.orderNumber)).toHaveCount(0);
    await shot(page, 'second-lab-id-chosen');

    // A STAT order comes first
    const [s1] = await rest.placeOrders(patient.uuid, [inst.numeric2.uuid], { hours: 6, urgency: 'STAT' });
    const labS = uniqueLabId('B1S');
    await rest.collect(patient.uuid, labS, [s1.uuid], undefined, 5);
    await openLabId(page, patient.uuid, 'enter');
    await expect(page.locator('.lr-group-choice')).toHaveCount(3);
    await expect(page.locator('.lr-group-choice').first()).toContainText(labS);
    await expect(page.locator('.lr-group-choice.lr-current')).toContainText(labS);
    await shot(page, 'stat-first');

    // A single Lab ID: no choice row
    const single = await collectedOrders(rest, [inst.numeric.uuid], 'B1');
    await openLabId(page, single.patient.uuid, 'enter');
    await expect(page.locator('.lr-group-choice')).toHaveCount(0);
    await shot(page, 'single-lab-id-no-choices');
  });

  test('B2 – Orders of two order encounters sharing a Lab ID show together', async ({ inst, rest, as, shot }) => {
    const patient = await rest.createPatient();
    const [o1] = await rest.placeOrders(patient.uuid, [inst.numeric.uuid], { hours: 9 });
    const [o2] = await rest.placeOrders(patient.uuid, [inst.numeric2.uuid], { hours: 8 });
    expect(o1.encounter).not.toBe(o2.encounter);
    const labId = uniqueLabId('B2');
    await rest.collect(patient.uuid, labId, [o1.uuid, o2.uuid]);
    const page = await as(inst.users.tech);
    await openLabId(page, patient.uuid, 'enter');
    await expect(page.locator('.lr-specimen')).toHaveCount(1);
    const section = page.locator('.lr-specimen').first();
    await expect(section.locator('.lr-order')).toHaveCount(2);
    await expect(lrOrder(page, o1.orderNumber)).toBeVisible();
    await expect(lrOrder(page, o2.orderNumber)).toBeVisible();
    await expect(page.locator('.lr-group-choice')).toHaveCount(0);
    await shot(page, 'two-order-encounters-one-lab-id');
  });

  test('B3 – Only changed tests are saved', async ({ inst, rest, as, shot }) => {
    const validation = inst.validationDefault;
    const c = await collectedOrders(rest, [inst.numeric.uuid, inst.numeric2.uuid], 'B3');
    const [o1, o2] = c.orders;
    const page = await as(inst.users.tech);
    const posts: any[] = [];
    page.on('request', r => { if (r.method() === 'POST' && /encounterFulfillingOrders/.test(r.url())) posts.push(r.postDataJSON()); });
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, o1.orderNumber);
    await block.locator('.result-value-field').first().fill(inst.numeric.normal);
    await setLrStatus(block, 'COMPLETED');
    await expect(block.locator('.lr-status-cell button.active')).toHaveText(validation ? 'Submit for validation' : 'Completed');
    await shot(page, 'one-of-two-entered');
    expect(await saveLabId(page)).toBe('reloaded');
    await shot(page, 'after-save');

    expect(posts).toHaveLength(1);
    expect(posts[0].orders).toEqual([{ uuid: o1.uuid, fulfillerStatus: 'COMPLETED' }]);
    const orders = await rest.labOrders(c.patient.uuid);
    const s1 = orders.find(o => o.uuid === o1.uuid), s2 = orders.find(o => o.uuid === o2.uuid);
    expect(s1.fulfillerStatus).toBe(validation ? 'IN_PROGRESS' : 'COMPLETED');
    expect(s2.fulfillerStatus, 'the untouched order keeps its status').toBe('IN_PROGRESS');
    expect(await rest.events(o2.uuid), 'no event for the untouched order').toEqual([]);
    const linkedToO2 = flatten(await rest.encounterObs(c.specimen)).filter(x => x.obs.order?.uuid === o2.uuid && x.obs.concept.uuid !== inst.orderNumberConcept);
    expect(linkedToO2.map(x => x.obs.display), 'no result or result date for the untouched order').toEqual([]);

    // Saving again with no changes
    posts.length = 0;
    expect(await saveLabId(page)).toBe('errors');
    expect(await lrErrors(page)).toContain('There are no changes to save.');
    expect(posts).toHaveLength(0);
    await shot(page, 'nothing-to-save');
  });

  test('B4 – Orders on hold are left on hold', async ({ inst, rest, as, shot }) => {
    const c = await collectedOrders(rest, [inst.numeric.uuid, inst.numeric2.uuid], 'B4');
    const [held, other] = c.orders;
    await rest.setOnHold(held.uuid);
    expect((await rest.labOrder(c.patient.uuid, held.uuid)).fulfillerStatus).toBe('ON_HOLD');
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    await shot(page, 'one-order-on-hold');
    const block = lrOrder(page, other.orderNumber);
    await block.locator('.result-value-field').first().fill(inst.numeric2.normal);
    await setLrStatus(block, 'COMPLETED');
    expect(await saveLabId(page)).toBe('reloaded');
    await shot(page, 'after-save');
    expect((await rest.labOrder(c.patient.uuid, held.uuid)).fulfillerStatus).toBe('ON_HOLD');
    expect((await rest.labOrder(c.patient.uuid, other.uuid)).fulfillerStatus).toBe(inst.validationDefault ? 'IN_PROGRESS' : 'COMPLETED');
  });

  test('B5 – A failed save of one specimen is not repeated', async ({ inst, rest, as, shot }) => {
    const patient = await rest.createPatient();
    const [o1, o2] = await rest.placeOrders(patient.uuid, [inst.numeric.uuid, inst.numeric2.uuid]);
    const labId = uniqueLabId('B5');
    const spec1 = await rest.collect(patient.uuid, labId, [o1.uuid], undefined, 7);
    const spec2 = await rest.collect(patient.uuid, labId, [o2.uuid], undefined, 6);
    const page = await as(inst.users.tech);
    const posts: string[] = [];
    page.on('request', r => { if (r.method() === 'POST' && /encounterFulfillingOrders/.test(r.url())) posts.push(r.url().split('/').pop()!); });
    await openLabId(page, patient.uuid, 'enter');
    await expect(page.locator('.lr-specimen')).toHaveCount(2);
    await lrOrder(page, o1.orderNumber).locator('.result-value-field').first().fill(inst.numeric.normal);
    await setLrStatus(lrOrder(page, o1.orderNumber), 'COMPLETED');
    await lrOrder(page, o2.orderNumber).locator('.result-value-field').first().fill(inst.numeric2.normal);
    await setLrStatus(lrOrder(page, o2.orderNumber), 'COMPLETED');

    // The second specimen's request fails, whichever the page saves second
    let n = 0;
    const pattern = '**/ws/rest/v1/encounterFulfillingOrders/*';
    await page.route(pattern, route => (route.request().method() === 'POST' && ++n === 2)
      ? route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ error: { message: 'Simulated failure' } }) })
      : route.continue());
    expect(await saveLabId(page)).toBe('errors');
    expect(posts).toHaveLength(2);
    const [savedSpec, failedSpec] = posts;
    const savedOrder = savedSpec === spec1 ? o1 : o2;
    const savedCard = page.locator('.lr-specimen', { has: lrOrder(page, savedOrder.orderNumber) });
    await expect(savedCard).toHaveClass(/lr-saved/);
    expect(await lrErrors(page)).toContain('Already saved: Lab ID ' + labId);
    expect(await lrErrors(page)).toContain('Simulated failure');
    await shot(page, 'second-specimen-failed');

    await page.unroute(pattern);
    posts.length = 0;
    expect(await saveLabId(page)).toBe('reloaded');
    expect(posts, 'only the specimen that failed is sent again').toEqual([failedSpec]);
    await shot(page, 'after-retry');
    for (const [spec, o, concept] of [[spec1, o1, inst.numeric.uuid], [spec2, o2, inst.numeric2.uuid]] as const) {
      const results = flatten(await rest.encounterObs(spec)).filter(x => x.obs.concept.uuid === concept && x.obs.order?.uuid === o.uuid);
      expect(results, 'exactly one result for ' + o.orderNumber).toHaveLength(1);
    }
  });

  test('B6 – More than 100 orders', async ({ inst, rest, as, shot }) => {
    test.skip(inst.key === 'rwanda', 'Environment: on the Rwanda copy one encounter with 101 orders keeps a database transaction ' +
      'open for many minutes (billing of each order) and blocks every login meanwhile; tested on Sierra Leone and Haiti');
    test.setTimeout(400_000);
    const patient = await rest.createPatient();
    const care = await rest.careSetting(), orderer = await rest.orderer();
    const place = async (n: number) => {
      const enc = await rest.post('encounter', {
        patient: patient.uuid, encounterType: inst.orderEncounterType, location: inst.orderLocation,
        encounterDatetime: new Date(Date.now() - 8 * 3600e3).toISOString().replace('Z', '+0000'),
        encounterProviders: [{ provider: orderer, encounterRole: inst.orderEncounterRole }],
        orders: Array.from({ length: n }, (_, i) => ({ type: 'testorder', patient: patient.uuid, concept: i % 2 ? inst.numeric.uuid : inst.numeric2.uuid, careSetting: care, orderer, orderType: inst.orderType })),
      });
      const orders = (await rest.get('encounter/' + enc.uuid + '?v=custom:(orders:(uuid))')).orders.map((o: any) => o.uuid);
      await rest.collect(patient.uuid, uniqueLabId('B6'), orders);
      return orders;
    };
    expect(await place(101)).toHaveLength(101);
    const page = await as(inst.users.tech);
    await openLabId(page, patient.uuid, 'enter');
    await expect(page.locator('.lr-order')).toHaveCount(100);
    await shot(page, 'labid-101-orders');
    // Evidence: the page shows the note when GET pihapps/labOrder?limit=100 has a "next" link
    const query = 'pihapps/labOrder?patient=' + patient.uuid + '&orderFulfillmentStatus=IN_FULFILLMENT&limit=100&v=custom:(uuid)';
    const r = await rest.get(query);
    expect.soft(r.results.length).toBe(100);
    expect.soft((r.links ?? []).some((l: any) => l.rel === 'next'),
      'PRODUCT: GET pihapps/labOrder with limit=100 and 101 matching orders has no "next" link (LabOrderRestController: ' +
      'hasMoreResults = startIndex + limit + 1 < totalCount, off by one), so the page cannot tell there are more').toBe(true);
    await expect.soft(page.locator('.lr-note-top'), 'note for 101 orders').toHaveText('Only the first 100 orders of the patient are shown.', { timeout: 3000 });

    // With one more order (102) the note shows: the page side works
    await place(1);
    await openLabId(page, patient.uuid, 'enter');
    await expect(page.locator('.lr-note-top')).toHaveText('Only the first 100 orders of the patient are shown.');
    await shot(page.locator('.lr-note-top'), 'note-with-102-orders');
  });
});
