import { request, APIRequestContext, expect } from '@playwright/test';
import { Creds, Instance } from './instances';

export const basic = (c: Creds) => 'Basic ' + Buffer.from(c.username + ':' + c.password).toString('base64');

const iso = (d: Date) => d.toISOString().replace('Z', '+0000');
const hoursAgo = (h: number) => new Date(Date.now() - h * 3600 * 1000);

export interface PlacedOrder { uuid: string; orderNumber: string; concept: string; encounter: string; }
export interface Patient { uuid: string; identifier: string; name: string; }

export interface RestResponse { status: number; body: any; }

/** A REST client for one instance and user, Basic auth on every request. */
export class Rest {
  private contexts = new Map<string, APIRequestContext>();
  private cache: Record<string, string> = {};
  constructor(public inst: Instance) {}

  private async ctx(user: Creds): Promise<APIRequestContext> {
    let c = this.contexts.get(user.username);
    if (!c) {
      c = await request.newContext({ baseURL: this.inst.baseURL + '/ws/rest/v1/', extraHTTPHeaders: { Authorization: basic(user) } });
      this.contexts.set(user.username, c);
    }
    return c;
  }

  async dispose() { for (const c of this.contexts.values()) await c.dispose(); this.contexts.clear(); }

  async call(method: 'GET' | 'POST' | 'DELETE', path: string, body?: any, user: Creds = this.inst.users.admin): Promise<RestResponse> {
    const c = await this.ctx(user);
    const r = await c.fetch(path, { method, data: body, headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined });
    const text = await r.text();
    let parsed: any = text;
    try { parsed = text ? JSON.parse(text) : null; } catch { /* keep text */ }
    return { status: r.status(), body: parsed };
  }

  async get(path: string, user?: Creds) { const r = await this.call('GET', path, undefined, user); return must(r, 'GET ' + path); }
  async post(path: string, body: any, user?: Creds) { const r = await this.call('POST', path, body, user); return must(r, 'POST ' + path); }

  // ---- setup ----

  async careSetting(): Promise<string> {
    if (this.inst.careSetting) return this.inst.careSetting;
    if (!this.cache.care) {
      const cs = (await this.get('caresetting?v=custom:(uuid,display)')).results;
      this.cache.care = (cs.find((c: any) => /OUTPATIENT/i.test(c.display)) ?? cs[0]).uuid;
    }
    return this.cache.care;
  }

  async orderer(): Promise<string> {
    if (this.inst.orderer) return this.inst.orderer;
    if (!this.cache.orderer) {
      const admin = (await this.get('user?username=admin&v=custom:(uuid,person:(uuid))')).results[0]
        ?? (await this.get('session?v=custom:(user:(uuid,person:(uuid)))')).user;
      const ps = (await this.get('provider?v=custom:(uuid,person:(uuid))&q=admin')).results;
      const mine = ps.find((p: any) => p.person?.uuid === admin.person.uuid);
      if (!mine) throw new Error('No provider for admin on ' + this.inst.key);
      this.cache.orderer = mine.uuid;
    }
    return this.cache.orderer;
  }

  async newIdentifier(): Promise<string> {
    if (this.inst.identifierSource) {
      const r = await this.post('idgen/identifiersource/' + this.inst.identifierSource + '/identifier', {});
      return r.identifier;
    }
    return 'E2E' + Date.now().toString(36).toUpperCase() + Math.floor(Math.random() * 1000);
  }

  async createPatient(givenName = 'Etoe'): Promise<Patient> {
    // Letters only: some sites reject digits in names
    const familyName = 'Rwa' + Array.from({ length: 7 }, () => 'abcdefghijklmnopqrstuvwxyz'[Math.floor(Math.random() * 26)]).join('');
    for (let attempt = 0; attempt < 10; attempt++) {
      const identifier = await this.newIdentifier();
      const r = await this.call('POST', 'patient', {
        person: { names: [{ givenName, familyName }], gender: 'F', birthdate: '1990-05-05' },
        identifiers: [{ identifier, identifierType: this.inst.identifierType, location: this.inst.sessionLocation, preferred: true }],
      });
      if (r.status < 300) return { uuid: r.body.uuid, identifier, name: givenName + ' ' + familyName };
      if (!/identifier|check digit|invalid/i.test(JSON.stringify(r.body))) must(r, 'create patient');
    }
    throw new Error('Unable to create a patient on ' + this.inst.key);
  }

  /** Places the tests in one order encounter, ordered `hours` ago. */
  async placeOrders(patient: string, concepts: string[], opts: { hours?: number; urgency?: 'ROUTINE' | 'STAT' } = {}): Promise<PlacedOrder[]> {
    const care = await this.careSetting();
    const orderer = await this.orderer();
    const enc = await this.post('encounter', {
      patient, encounterType: this.inst.orderEncounterType, location: this.inst.orderLocation,
      encounterDatetime: iso(hoursAgo(opts.hours ?? 8)),
      encounterProviders: [{ provider: orderer, encounterRole: this.inst.orderEncounterRole }],
      orders: concepts.map(c => ({ type: 'testorder', patient, concept: c, careSetting: care, orderer, orderType: this.inst.orderType, urgency: opts.urgency ?? 'ROUTINE' })),
    });
    const orders = await this.get('encounter/' + enc.uuid + '?v=custom:(orders:(uuid,orderNumber,concept:(uuid)))');
    return concepts.map(c => {
      const o = orders.orders.find((x: any) => x.concept.uuid === c);
      return { uuid: o.uuid, orderNumber: o.orderNumber, concept: c, encounter: enc.uuid };
    });
  }

  /** Records a specimen collection with a Lab ID for the orders, as the specimen collection form does. */
  async collect(patient: string, labId: string, orders: string[], user: Creds = this.inst.users.tech, hours = 7): Promise<string> {
    const r = await this.post('encounterFulfillingOrders', {
      encounter: {
        patient, encounterType: this.inst.specimenEncounterType, encounterDatetime: iso(hoursAgo(hours)), location: this.inst.sessionLocation,
        obs: [{ concept: this.inst.labIdConcept, value: labId, formNamespaceAndPath: 'pihapps^specimenCollection/lab_id' }]
          .concat(orders.map((o, i) => ({ concept: this.inst.orderNumberConcept, valueText: '', order: o, formNamespaceAndPath: 'pihapps^specimenCollection/order_number_' + i } as any))),
      },
      orders,
    }, user);
    return r.encounter?.uuid ?? r.uuid;
  }

  /** Saves results through encounterFulfillingOrders as the result pages do. obs: [{concept, value, order}] */
  async saveResults(encounter: string, obs: any[], orders: { uuid: string; fulfillerStatus: string }[], user: Creds = this.inst.users.tech, extra: any = {}) {
    return this.post('encounterFulfillingOrders/' + encounter, { encounter: { uuid: encounter, obs }, orders, ...extra }, user);
  }

  async setOnHold(order: string) {
    return this.post('order/' + order + '/fulfillerdetails', { fulfillerStatus: 'ON_HOLD' });
  }

  // ---- reading ----

  async labOrders(patient: string): Promise<any[]> {
    return (await this.get('pihapps/labOrder?patient=' + patient + '&limit=200&v=custom:(uuid,orderNumber,fulfillerStatus,accessionNumber,labResultValidation,dateActivated,fulfillerEncounter:(uuid),concept:(uuid,display))')).results;
  }

  async labOrder(patient: string, order: string): Promise<any> {
    const o = (await this.labOrders(patient)).find(x => x.uuid === order);
    if (!o) throw new Error('Order ' + order + ' not found for patient');
    return o;
  }

  async validationInfo(orders: string[], user: Creds = this.inst.users.admin): Promise<Record<string, any>> {
    return this.get('pihapps/labResultValidation?' + orders.map(o => 'order=' + o).join('&'), user);
  }

  async events(order: string): Promise<{ eventType: string; comment: string | null; creator: string | null }[]> {
    return (await this.validationInfo([order]))[order].history;
  }

  async decide(action: 'VALIDATE' | 'RETURN', orders: string[], user: Creds, comment?: string, results?: Record<string, string[]>): Promise<RestResponse> {
    return this.call('POST', 'pihapps/labResultValidation', { action, orders, comment, results }, user);
  }

  /** The top-level obs of an encounter, with four levels of members (a culture's antibiotic results are at level 4). */
  async encounterObs(encounter: string): Promise<any[]> {
    const leaf = 'uuid,display,concept:(uuid),status';
    const lvl4 = leaf + ',groupMembers:(' + leaf + ')';
    const lvl3 = leaf + ',previousVersion:(uuid),groupMembers:(' + lvl4 + ')';
    const lvl2 = leaf + ',order:(uuid),previousVersion:(uuid),groupMembers:(' + lvl3 + ')';
    const v = 'custom:(obs:(uuid,display,concept:(uuid),order:(uuid),status,voided,previousVersion:(uuid),groupMembers:(' + lvl2 + ')))';
    return (await this.get('encounter/' + encounter + '?v=' + v)).obs;
  }

  async obs(uuid: string): Promise<any> {
    return this.get('obs/' + uuid + '?v=custom:(uuid,display,status,voided,previousVersion:(uuid),concept:(uuid),order:(uuid),obsGroup:(uuid),value)');
  }

  async labResults(patient: string, statuses?: string[]): Promise<any[]> {
    const q = 'pihapps/labResults?patient=' + patient + (statuses ? statuses.map(s => '&status=' + s).join('') : '') + '&v=custom:(uuid,display,status,concept:(uuid),order:(uuid))&limit=100';
    return (await this.get(q)).results;
  }

  async gp(name: string): Promise<string | null> {
    const r = await this.get('systemsetting?q=' + encodeURIComponent(name) + '&v=custom:(property,value)');
    return r.results.find((x: any) => x.property === name)?.value ?? null;
  }

  async setGp(name: string, value: string) {
    const existing = (await this.get('systemsetting?q=' + encodeURIComponent(name) + '&v=custom:(uuid,property)')).results.find((x: any) => x.property === name);
    if (existing) return this.post('systemsetting/' + existing.uuid, { value });
    return this.post('systemsetting', { property: name, value });
  }

  async setValidation(on: boolean) {
    await this.setGp('pihapps.labs.requireResultValidation', String(on));
    const cfg = await this.get('pihapps/config?v=custom:(labOrderConfig:(requireResultValidation))');
    expect(cfg.labOrderConfig.requireResultValidation, 'requireResultValidation on ' + this.inst.key).toBe(on);
  }
}

export function must(r: RestResponse, what: string) {
  if (r.status >= 300) throw new Error(what + ' -> HTTP ' + r.status + ': ' + JSON.stringify(r.body).slice(0, 800));
  return r.body;
}

/** Flattens an encounter obs tree to [{depth, path, obs}] for assertions. */
export function flatten(obs: any[], depth = 0, parent: any = null, out: any[] = []) {
  for (const o of obs ?? []) {
    out.push({ depth, parent, obs: o });
    if (o.groupMembers) flatten(o.groupMembers, depth + 1, o, out);
  }
  return out;
}

export const uniqueLabId = (prefix: string) => prefix + '-' + Date.now().toString(36).toUpperCase().slice(-5) + Math.floor(Math.random() * 90 + 10);

export interface Collected { patient: Patient; orders: PlacedOrder[]; labId: string; specimen: string; }

/** A new patient with the tests ordered together and collected with one new Lab ID. */
export async function collectedOrders(rest: Rest, concepts: string[], prefix = 'E2E', opts: { urgency?: 'ROUTINE' | 'STAT' } = {}): Promise<Collected> {
  const patient = await rest.createPatient();
  const orders = await rest.placeOrders(patient.uuid, concepts, opts);
  const labId = uniqueLabId(prefix);
  const specimen = await rest.collect(patient.uuid, labId, orders.map(o => o.uuid));
  return { patient, orders, labId, specimen };
}

/** Enters results by REST as the result pages do: values [{order, concept, value}], each order given the status. */
export async function enterViaRest(rest: Rest, specimen: string, values: { order: string; concept: string; value: any }[],
                                   status: 'COMPLETED' | 'IN_PROGRESS' = 'COMPLETED', user?: Creds) {
  const orders = [...new Set(values.map(v => v.order))].map(uuid => ({ uuid, fulfillerStatus: status }));
  return rest.saveResults(specimen, values.map(v => ({ concept: v.concept, value: v.value, order: v.order })), orders, user ?? rest.inst.users.tech);
}

/** Validates the orders by REST as the validator, with the results the validation GET returns. */
export async function validateViaRest(rest: Rest, orders: string[], user?: Creds) {
  const u = user ?? rest.inst.users.validator;
  const info = await rest.validationInfo(orders, u);
  const results: Record<string, string[]> = {};
  orders.forEach(o => { results[o] = info[o].results; });
  return must(await rest.decide('VALIDATE', orders, u, undefined, results), 'validate');
}

/** The non-voided result obs of an order in its specimen encounter, at any depth. */
export async function resultsOf(rest: Rest, specimen: string, conceptUuid: string, orderUuid?: string) {
  return flatten(await rest.encounterObs(specimen))
    .filter(x => x.obs.concept.uuid === conceptUuid && (!orderUuid || x.obs.order?.uuid === orderUuid || x.parent));
}
