import { test, expect, withValidation } from '../lib/fixtures';
import { Page, Locator } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import { Rest, Collected, collectedOrders, validateViaRest, flatten, must } from '../lib/rest';
import { Instance } from '../lib/instances';
import {
  openLabId, lrOrder, setLrStatus, saveLabId, resultRow, fillResult, recordToasts, toasts,
  openOrderList, filterOrderListOnPatient, openSingleForm, singleForm, singleSave,
} from '../lib/pages';

// Rwanda's bacteriology concepts (distro rwandaemr-content concepts/bacteriology.csv) on the Rwanda copy
const B = {
  urines: 'f2733e52-1150-55e5-9f99-b66360e32670',       // Bact_Urines (the exam, a panel)
  wetMount: 'b1f005ed-d449-505c-8420-afe5226b3988',     // Bact_Wet mount findings (a panel in the exam)
  wmWbc: '4db1b77d-4512-54e2-a307-6c6bc18ed726',
  wmRbc: '3be34554-9fa9-5a6f-8d2d-b9f2805dc165',
  gram: 'd3e01380-4de0-517c-bd9b-06f3aa407970',         // Bact_Gram stain findings (a panel in the exam)
  gsGpc: 'eb8a5c08-5de3-5865-b99a-8f09b4492b0e',
  culture: 'e4f7a701-60b5-52f8-aabf-fd10d9e1c045',
  organismIsolated: 'a2e60961-48c1-5b51-b645-c86dcc8e9f60',
  noOrganism: 'b15cced0-1fa3-50a8-a7b4-1919d3d80761',
  susceptibility: '6f65e869-9395-5c8b-8a65-05ad3e19bcb4',
  isolate: 'b606710c-ee65-514b-b395-77629a1c1204',
  organism: '63cd7913-9e0c-58ed-a4c9-31d3134d5d62',
  abxResult: '7876a350-21bb-59c8-874d-2deec23b850d',
  antibiotic: '7d2d0d97-4c19-53f2-928b-5a6e7455b27c',
  interpretation: 'e11de02b-3000-52ed-8ded-cf9553fe792f',
  ecoli: 'd85e7f71-92e4-56df-91d7-16ad85123265',
  saureus: '9efeba4b-98d3-5dae-9bd3-59513c25dca0',
  klebsiellaSpecies: '604f2244-3522-4267-88b4-fc7471ad2aef',   // not an answer of Bact_Isolated organism
  amikacin: '3cda17e0-26fe-102b-80cb-0017a47871b2',
  amoxClav: '8d077011-28b7-587b-82ec-4433d457fdef',
  S: '3cda50de-26fe-102b-80cb-0017a47871b2',
  R: '3cda5552-26fe-102b-80cb-0017a47871b2',
};

const susRow = (scope: Page | Locator) => resultRow(scope, B.susceptibility);
const dialog = (page: Page) => page.locator('.bact-dialog');
const sir = (page: Page, germ: number, antibiotic: string, value: string) =>
  dialog(page).locator('input[data-germ="' + germ + '"][data-antibiotic="' + antibiotic + '"][value="' + value + '"]');

/** Enters an Urines exam on the Lab ID page: wet mount, Gram stain, culture with E. coli (amikacin S, amox-clav R). */
async function enterCulture(page: Page, order: string, opts: { submit?: boolean } = {}) {
  const block = lrOrder(page, order);
  await fillResult(block, B.wmWbc, 'few');
  await fillResult(block, B.gsGpc, 'present');
  await resultRow(block, B.culture).locator('select.result-value-field').first().selectOption(B.organismIsolated);
  await expect(susRow(block)).toBeVisible();
  await susRow(block).locator('.bact-enter-button').click();
  await dialog(page).locator('select[data-germ="0"]').selectOption(B.ecoli);
  await sir(page, 0, B.amikacin, B.S).click();
  await sir(page, 0, B.amoxClav, B.R).click();
  await dialog(page).locator('button.confirm').click();
  await expect(dialog(page)).toBeHidden();
  if (opts.submit !== false) await setLrStatus(block, 'COMPLETED');
  return block;
}

async function cultureOrder(rest: Rest, prefix: string) {
  return collectedOrders(rest, [B.urines], prefix);
}

function examGroup(tree: any[], order: string) {
  const groups = tree.filter(o => o.concept.uuid === B.urines && o.order?.uuid === order);
  expect(groups, 'one Bact_Urines group linked to the order').toHaveLength(1);
  return groups[0];
}

test.describe('F. Culture / antimicrobial susceptibility (Rwanda)', () => {
  test.beforeEach(async ({ inst }) => {
    test.skip(!inst.hasBacteriology, 'Rwanda only: the bacteriology editor and renderer are registered by rwandaemr and the distro');
  });

  test('F1 – Exam layout', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F1');
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, c.orders[0].orderNumber);
    await expect(block).toContainText(/Wet mount/);
    await expect(block).toContainText(/Gram stain/);
    // Document order: wet mount, Gram stain, culture, susceptibility
    const positions = await page.evaluate((uuids) => {
      const all = Array.from(document.querySelectorAll('.lr-order .result-row'));
      return uuids.map(u => all.findIndex(e => e.getAttribute('data-concept-uuid') === u));
    }, [B.wmWbc, B.gsGpc, B.culture, B.susceptibility]);
    expect(positions.every(p => p >= 0), 'rows present: ' + positions).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
    await expect(resultRow(block, B.wmWbc).locator('.result-value-field').first()).toHaveAttribute('type', /text/);
    await expect(susRow(block), 'susceptibility hidden until an organism is isolated').toBeHidden();
    await shot(page, 'urines-layout');
  });

  test('F2 – Susceptibility appears once an organism is isolated', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F2');
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, c.orders[0].orderNumber);
    await expect(susRow(block)).toBeHidden();
    await resultRow(block, B.culture).locator('select.result-value-field').first().selectOption(B.organismIsolated);
    await expect(susRow(block)).toBeVisible();
    await expect(susRow(block).locator('.bact-enter-button')).toBeVisible();
    await shot(page, 'susceptibility-row-shown');
  });

  test('F3 – Enter susceptibility (validation on)', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F3');
    const order = c.orders[0];
    const page = await as(inst.users.tech);
    const posts: string[] = [];
    page.on('request', r => { if (r.method() === 'POST' && /\/ws\/rest\//.test(r.url())) posts.push(r.url().replace(/.*\/ws\/rest\/v1\//, '')); });
    await openLabId(page, c.patient.uuid, 'enter');
    const block = await enterCulture(page, order.orderNumber);
    await expect(susRow(block)).toContainText(/coli: 1 S · 0 I · 1 R/);
    await expect(block.locator('.lr-status-cell button.active')).toHaveText('Submit for validation');
    await shot(page, 'culture-entered');
    expect(await saveLabId(page)).toBe('reloaded');
    expect(posts.filter(p => !/^session/.test(p)), 'saved in one request').toEqual(['encounterFulfillingOrders/' + c.specimen]);

    const group = examGroup(await rest.encounterObs(c.specimen), order.uuid);
    const members = group.groupMembers.map((m: any) => m.concept.uuid);
    expect(members).toEqual(expect.arrayContaining([B.wetMount, B.gram, B.culture, B.susceptibility]));
    const wet = group.groupMembers.find((m: any) => m.concept.uuid === B.wetMount);
    expect(wet.groupMembers.map((m: any) => m.concept.uuid), 'wet mount result inside its nested group').toContain(B.wmWbc);
    const all = flatten([group]);
    expect(new Set(all.map(x => x.obs.status)), 'the whole group is preliminary').toEqual(new Set(['PRELIMINARY']));
    expect(all.some(x => x.obs.concept.uuid === B.organism)).toBe(true);
    expect(all.filter(x => x.obs.concept.uuid === B.interpretation)).toHaveLength(2);
    const o = await rest.labOrder(c.patient.uuid, order.uuid);
    expect(o.fulfillerStatus).toBe('IN_PROGRESS');
    expect(o.labResultValidation.eventType).toBe('RESULTS_SUBMITTED');
  });

  test('F4 – Dialog rules', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F4');
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, c.orders[0].orderNumber);
    await resultRow(block, B.culture).locator('select.result-value-field').first().selectOption(B.organismIsolated);
    await susRow(block).locator('.bact-enter-button').click();
    await dialog(page).locator('select[data-germ="0"]').selectOption(B.ecoli);
    await dialog(page).locator('select[data-germ="1"]').selectOption(B.ecoli);
    await sir(page, 0, B.amikacin, B.S).click();
    await dialog(page).locator('button.confirm').click();
    await expect(dialog(page).locator('.bact-errors')).toHaveText('The same organism is chosen for two germs');
    await shot(page, 'same-organism-twice');
    await dialog(page).locator('select[data-germ="1"]').selectOption('');
    // Clicking the chosen S/I/R again clears it
    await expect(sir(page, 0, B.amikacin, B.S)).toBeChecked();
    await sir(page, 0, B.amikacin, B.S).click();
    await expect(sir(page, 0, B.amikacin, B.S)).not.toBeChecked();
    await sir(page, 0, B.amikacin, B.S).click();
    // Search, and only antibiotics with a result
    const rows = dialog(page).locator('.bact-grid tr[data-name]');
    const total = await rows.count();
    await dialog(page).locator('.bact-search').fill('amika');
    await expect.poll(async () => (await rows.filter({ visible: true }).count())).toBeLessThan(total);
    await expect(rows.filter({ visible: true }).first()).toContainText(/AMIKACIN/i);
    await dialog(page).locator('.bact-search').fill('');
    await dialog(page).locator('.bact-tools input[type="checkbox"]').check();
    await expect.poll(async () => rows.filter({ visible: true }).count()).toBe(1);
    await shot(page, 'only-with-result');
    // Cancel discards
    await dialog(page).getByRole('button', { name: 'Cancel' }).click();
    await expect(dialog(page)).toBeHidden();
    await expect(susRow(block)).not.toContainText(/coli/);
  });

  test('F4 – Dialog labels translated (French locale)', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F4FR');
    const fr = await as(inst.users.tech, { locale: 'fr' });
    await openLabId(fr, c.patient.uuid, 'enter');
    await expect(fr.locator('.lr-hint'), 'the page itself is in French').toContainText('validés');
    const block = lrOrder(fr, c.orders[0].orderNumber);
    await resultRow(block, B.culture).locator('select.result-value-field').first().selectOption(B.organismIsolated);
    const enter = susRow(block).locator('.bact-enter-button');
    await enter.click();
    await shot(fr, 'dialog-french');
    const labels = { enter: (await enter.innerText()).trim(), actions: (await dialog(fr).locator('.bact-actions').innerText()).replace(/\s+/g, ' ').trim(),
      onlyWithResult: (await dialog(fr).locator('.bact-tools label').innerText()).trim() };
    test.info().annotations.push({ type: 'labels in a French session', description: JSON.stringify(labels) });
    expect.soft(labels.enter).toBe('Saisir');
    expect.soft(labels.actions).toContain('Annuler');
    expect.soft(labels.actions).toContain('Terminer');
    expect.soft(labels.onlyWithResult).toBe('Seulement les antibiotiques avec un résultat');
  });

  test('F5 – Validate a culture', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F5');
    const order = c.orders[0];
    const tech = await as(inst.users.tech);
    await openLabId(tech, c.patient.uuid, 'enter');
    await enterCulture(tech, order.orderNumber);
    expect(await saveLabId(tech)).toBe('reloaded');
    const val = await as(inst.users.validator);
    await openLabId(val, c.patient.uuid, 'validate');
    const block = lrOrder(val, order.orderNumber);
    await expect(block.locator('.bact-table')).toBeVisible();
    await expect(block.locator('.bact-legend')).toBeVisible();
    await expect(block.locator('.bact-table')).toContainText(/coli/);
    await expect(block.locator('.bact-table [class*="bact-code-"]')).toHaveCount(2);
    await expect(block.locator('.lr-subpanel-name').first()).toContainText(/Wet mount/);
    await shot(val, 'validate-culture');
    expect(await saveLabId(val)).toBe('reloaded');
    const all = flatten([examGroup(await rest.encounterObs(c.specimen), order.uuid)]);
    expect(new Set(all.map(x => x.obs.status)), 'the whole group is final').toEqual(new Set(['FINAL']));
    expect((await rest.labOrder(c.patient.uuid, order.uuid)).fulfillerStatus).toBe('COMPLETED');
  });

  test('F6 – Clinician view', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F6');
    const order = c.orders[0];
    const tech = await as(inst.users.tech);
    await openLabId(tech, c.patient.uuid, 'enter');
    await enterCulture(tech, order.orderNumber);
    expect(await saveLabId(tech)).toBe('reloaded');
    await validateViaRest(rest, [order.uuid]);
    // A second exam of the patient, submitted but not validated
    const [o2] = await rest.placeOrders(c.patient.uuid, [B.urines], { hours: 5 });
    const spec2 = await rest.collect(c.patient.uuid, c.labId + 'B', [o2.uuid], undefined, 4);
    must(await rest.call('POST', 'encounterFulfillingOrders/' + spec2, {
      encounter: { uuid: spec2, obs: [{ concept: B.urines, order: o2.uuid, groupMembers: [{ concept: B.wetMount, order: o2.uuid, groupMembers: [{ concept: B.wmWbc, value: 'abundant', order: o2.uuid }] }] }] },
      orders: [{ uuid: o2.uuid, fulfillerStatus: 'COMPLETED' }] }, inst.users.tech), 'submit second exam');
    const page = await as(inst.users.clinician);
    await page.goto('pihapps/labs/patientLabResults.page?patient=' + c.patient.uuid);
    await page.waitForLoadState('networkidle');
    const table = page.locator('#results-table');
    await expect(table.locator('.bact-table')).toBeVisible();
    await expect(table).toContainText(/Organism isolated/);
    await expect(table).toContainText('few');
    await expect(table.locator('tr.obs-group-row', { hasText: /Urines/ }).first()).toBeVisible();
    await expect(table.locator('tr.obs-subgroup-row', { hasText: /Wet mount/ }).first(), 'nested panel as a sub-heading').toBeVisible();
    await expect(table.locator('tr.obs-subgroup-row', { hasText: /Gram stain/ }).first()).toBeVisible();
    await expect(table.locator('.obs-subgroup-member-row', { hasText: /WBC/ }).first(), 'nested panel tests indented').toBeVisible();
    await expect(table, 'results awaiting validation are not shown').not.toContainText('abundant');
    await shot(page, 'clinician-culture');
  });

  test('F7 – Edit a validated culture', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F7');
    const order = c.orders[0];
    const tech = await as(inst.users.tech);
    await openLabId(tech, c.patient.uuid, 'enter');
    await enterCulture(tech, order.orderNumber);
    expect(await saveLabId(tech)).toBe('reloaded');
    await validateViaRest(rest, [order.uuid]);
    const susBefore = examGroup(await rest.encounterObs(c.specimen), order.uuid).groupMembers.find((m: any) => m.concept.uuid === B.susceptibility);

    // Variant: Enter, Done without changing anything, Save
    await openOrderList(tech, { labId: c.labId, statuses: ['COMPLETED_FULFILLMENT'] });
    await openSingleForm(tech, order.uuid);
    await susRow(singleForm(tech)).locator('.bact-enter-button').click();
    await dialog(tech).locator('button.confirm').click();
    expect(await singleSave(tech)).toBe('closed');
    let susNow = examGroup(await rest.encounterObs(c.specimen), order.uuid).groupMembers.find((m: any) => m.concept.uuid === B.susceptibility);
    expect(susNow.uuid, 'nothing changes').toBe(susBefore.uuid);
    expect((await rest.events(order.uuid)).map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED', 'RESULTS_VALIDATED']);

    // Change amox-clav from R to S
    await openOrderList(tech, { labId: c.labId, statuses: ['COMPLETED_FULFILLMENT'] });
    await openSingleForm(tech, order.uuid);
    await susRow(singleForm(tech)).locator('.bact-enter-button').click();
    await sir(tech, 0, B.amoxClav, B.S).click();
    await dialog(tech).locator('button.confirm').click();
    await expect(susRow(singleForm(tech))).toContainText(/coli: 2 S · 0 I · 0 R/);
    await shot(tech, 'single-form-edited-susceptibility');
    expect(await singleSave(tech)).toBe('closed');
    const group = examGroup(await rest.encounterObs(c.specimen), order.uuid);
    susNow = group.groupMembers.find((m: any) => m.concept.uuid === B.susceptibility);
    expect(susNow.uuid).not.toBe(susBefore.uuid);
    expect(susNow.status).toBe('AMENDED');
    expect(susNow.previousVersion?.uuid).toBe(susBefore.uuid);
    expect((await rest.obs(susBefore.uuid)).voided).toBe(true);
    expect((await rest.events(order.uuid)).map(e => e.eventType)).toEqual(['RESULTS_SUBMITTED', 'RESULTS_VALIDATED', 'RESULTS_AMENDED']);
  });

  test('F8 – Culture contradicts the susceptibility', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F8');
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    const block = await enterCulture(page, c.orders[0].orderNumber, { submit: false });
    await expect(block.locator('.bact-warning')).toBeHidden();
    await resultRow(block, B.culture).locator('select.result-value-field').first().selectOption(B.noOrganism);
    await expect(susRow(block)).toBeVisible();
    await expect(block.locator('.bact-warning')).toBeVisible();
    await expect(block.locator('.bact-warning')).toHaveText('The culture result does not say an organism was isolated');
    await shot(page, 'culture-contradicts');
  });

  test('F9 – Answers no longer configured are kept', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F9');
    const order = c.orders[0];
    // Recorded with an organism that is not (or no longer) an answer of Bact_Isolated organism
    const isolate = (antibiotic: string, interp: string) => ({ concept: B.abxResult, groupMembers: [{ concept: B.antibiotic, value: antibiotic }, { concept: B.interpretation, value: interp }] });
    must(await rest.call('POST', 'encounterFulfillingOrders/' + c.specimen, {
      encounter: { uuid: c.specimen, obs: [{ concept: B.urines, order: order.uuid, groupMembers: [{ concept: B.culture, value: B.organismIsolated, order: order.uuid }] }] },
      orders: [{ uuid: order.uuid, fulfillerStatus: 'IN_PROGRESS' }],
      editorResults: [{ order: order.uuid, concept: B.susceptibility, obs: { concept: B.susceptibility, groupMembers: [
        { concept: B.isolate, groupMembers: [{ concept: B.organism, value: B.klebsiellaSpecies }, isolate(B.amikacin, B.R)] }] } }],
    }, inst.users.tech), 'save culture with an unconfigured organism');
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    const block = lrOrder(page, order.orderNumber);
    await expect(susRow(block)).toContainText(/Klebsiella species/);
    await susRow(block).locator('.bact-enter-button').click();
    await expect(dialog(page).locator('select[data-germ="0"] option:checked')).toHaveText(/Klebsiella species/);
    await shot(page, 'dialog-shows-unconfigured-organism');
    await sir(page, 0, B.amoxClav, B.S).click();
    await dialog(page).locator('button.confirm').click();
    await setLrStatus(block, 'COMPLETED');
    expect(await saveLabId(page)).toBe('reloaded');
    const all = flatten([examGroup(await rest.encounterObs(c.specimen), order.uuid)]);
    const organism = all.find(x => x.obs.concept.uuid === B.organism);
    expect(organism?.obs.display, 'the organism is kept').toMatch(/Klebsiella species/);
    expect(all.filter(x => x.obs.concept.uuid === B.interpretation)).toHaveLength(2);
  });

  test('F10 – Printing', async ({ inst, rest, as, shot }, testInfo) => {
    const c = await cultureOrder(rest, 'F10');
    const order = c.orders[0];
    const tech = await as(inst.users.tech);
    await openLabId(tech, c.patient.uuid, 'enter');
    await enterCulture(tech, order.orderNumber);
    expect(await saveLabId(tech)).toBe('reloaded');
    await validateViaRest(rest, [order.uuid]);

    const page = await as(inst.users.tech, { initScript: () => {
      (window as any).__opened = [];
      window.open = ((u: string) => { (window as any).__opened.push(u); return null; }) as any;
    } });
    const printFor = async (patient: string) => {
      await openOrderList(page, { patientGrouping: true, statuses: ['COMPLETED_FULFILLMENT', 'IN_FULFILLMENT', 'AWAITING_VALIDATION'] });
      await page.locator('#group-by-patient-btn').click();
      await filterOrderListOnPatient(page, patient);
      const print = page.locator('#patients-table .lab-ext-group-action[data-extension-id="rwandaemr.lab.printResults"][data-patient-uuid="' + patient + '"]');
      await expect(print).toHaveClass(/fa-print/);
      await recordToasts(page);
      await print.click();
    };
    await printFor(c.patient.uuid);
    await expect.poll(() => page.evaluate(() => (window as any).__opened.length), { timeout: 20000 }).toBe(1);
    const b64 = await page.evaluate(async () => {
      const buf = await (await fetch((window as any).__opened[0])).arrayBuffer();
      let s = ''; new Uint8Array(buf).forEach(b => s += String.fromCharCode(b)); return btoa(s);
    });
    const pdf = testInfo.outputPath('results.pdf');
    fs.writeFileSync(pdf, Buffer.from(b64, 'base64'));
    await testInfo.attach('printed-results.pdf', { path: pdf, contentType: 'application/pdf' });
    const text = execFileSync('pdftotext', ['-layout', pdf, '-']).toString();
    await testInfo.attach('printed-results.txt', { body: text, contentType: 'text/plain' });
    expect(text).toContain(c.labId);
    expect(text).toMatch(/Wet mount/);
    expect(text).toMatch(/WBC/);
    expect(text).toContain('few');
    expect(text).toMatch(/Gram stain/);
    expect(text).toMatch(/Antimicrobial susceptibility/);
    expect(text).toMatch(/Escherichia coli/);
    expect(text).toMatch(/Sensitive/);
    expect(text).toMatch(/Resistant/);

    // A patient with nothing completed
    const none = await collectedOrders(rest, [inst.numeric.uuid], 'F10N');
    await page.evaluate(() => { (window as any).__opened = []; });
    await printFor(none.patient.uuid);
    await expect.poll(() => toasts(page)).toContain('errorMessage: There are no completed results to print');
    expect(await page.evaluate(() => (window as any).__opened.length)).toBe(0);
    await shot(page, 'nothing-to-print');
  });
});

test.describe('F. Culture with validation off (Rwanda)', () => {
  withValidation(false, { skip: ['sierra-leone', 'haiti'], reason: 'Rwanda only: bacteriology' });

  test('F3 – Enter susceptibility (validation off)', async ({ inst, rest, as, shot }) => {
    const c = await cultureOrder(rest, 'F3OFF');
    const order = c.orders[0];
    const page = await as(inst.users.tech);
    await openLabId(page, c.patient.uuid, 'enter');
    const block = await enterCulture(page, order.orderNumber);
    await expect(block.locator('.lr-status-cell button.active')).toHaveText('Completed');
    await shot(page, 'culture-entered-validation-off');
    expect(await saveLabId(page)).toBe('reloaded');
    const all = flatten([examGroup(await rest.encounterObs(c.specimen), order.uuid)]);
    expect(new Set(all.map(x => x.obs.status)), 'the whole group is final').toEqual(new Set(['FINAL']));
    expect((await rest.labOrder(c.patient.uuid, order.uuid)).fulfillerStatus).toBe('COMPLETED');
    expect(await rest.events(order.uuid)).toEqual([]);
  });
});
