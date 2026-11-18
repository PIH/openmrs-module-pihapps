import { request } from '@playwright/test';
import { Instance, selectedSites } from './instances';
import { Rest } from './rest';

/**
 * Stops the run when the server at a site's URL is not that site. Every site defaults to the same URL, so running the
 * wrong site against a server would create data and fail confusingly. Checks that the site's session location exists.
 */
async function checkSite(inst: Instance) {
  const c = await request.newContext();
  try {
    let r;
    try { r = await c.get(inst.baseURL + '/ws/rest/v1/session', { timeout: 10000 }); }
    catch (e) { throw new Error(inst.key + ': no OpenMRS server at ' + inst.baseURL + ' (' + e + ')'); }
    if (!r.ok()) throw new Error(inst.key + ': ' + inst.baseURL + ' answered HTTP ' + r.status());
  } finally { await c.dispose(); }
  const rest = new Rest(inst);
  try {
    const r = await rest.call('GET', 'location/' + inst.sessionLocation + '?v=custom:(uuid)');
    if (r.status !== 200) {
      throw new Error(inst.key + ': the server at ' + inst.baseURL + ' does not look like ' + inst.key +
        ' (session location ' + inst.sessionLocation + ' not found). Set E2E_SITES to the site that is running, ' +
        'or E2E_' + inst.key.toUpperCase().replace('-', '_') + '_URL to its address.');
    }
  } finally { await rest.dispose(); }
}

/** Creates the site's test users and their providers when missing, and a provider for admin. Idempotent. */
async function ensureUsers(rest: Rest, inst: Instance) {
  const roles: any[] = (await rest.get('role?v=custom:(uuid,display)&limit=500')).results;
  // A user with more than one provider account cannot open the UI pages, so only add one when the user has none
  const ensureProvider = async (userUuid: string, personUuid: string, identifier: string) => {
    const ps = (await rest.get('provider?user=' + userUuid + '&v=custom:(uuid,retired)')).results;
    if (!ps.some((p: any) => !p.retired)) await rest.post('provider', { person: personUuid, identifier });
  };
  for (const u of inst.testUsers) {
    const existing = (await rest.get('user?username=' + u.username + '&v=custom:(uuid,username,person:(uuid))')).results
      .find((x: any) => x.username === u.username);
    let person = existing?.person?.uuid;
    let user = existing?.uuid;
    if (!existing) {
      const roleUuids = u.roles.map(name => {
        const r = roles.find(x => x.display === name);
        if (!r) throw new Error(inst.key + ': role not found: ' + name);
        return r.uuid;
      });
      // Person names may only hold letters: e2e_validator2 becomes "Test Validator"
      const family = u.username.replace(/^e2e_/, '').replace(/[^a-z]/gi, '');
      const created = await rest.post('user', { username: u.username, password: u.password, roles: roleUuids,
        person: { names: [{ givenName: 'Test', familyName: family.charAt(0).toUpperCase() + family.slice(1) }], gender: 'F' } });
      user = created.uuid;
      person = (await rest.get('user/' + created.uuid + '?v=custom:(person:(uuid))')).person.uuid;
    }
    await ensureProvider(user, person, u.username);
  }
  const admin = (await rest.get('session?v=custom:(user:(uuid,person:(uuid)))')).user;
  await ensureProvider(admin.uuid, admin.person.uuid, 'admin');
}

export default async function globalSetup() {
  for (const inst of selectedSites()) {
    await checkSite(inst);
    const rest = new Rest(inst);
    try {
      await ensureUsers(rest, inst);
      await rest.setValidation(inst.validationDefault);
    } finally { await rest.dispose(); }
  }
}
