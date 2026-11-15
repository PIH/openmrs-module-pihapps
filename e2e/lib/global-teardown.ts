import { request } from '@playwright/test';
import { selectedSites } from './instances';
import { Rest } from './rest';

/** Restores pihapps.labs.requireResultValidation to each site's default. */
export default async function globalTeardown() {
  for (const inst of selectedSites()) {
    const c = await request.newContext();
    let up = false;
    try { up = (await c.get(inst.baseURL + '/ws/rest/v1/session', { timeout: 10000 })).ok(); } catch { up = false; }
    await c.dispose();
    if (!up) continue;
    const rest = new Rest(inst);
    try { await rest.setValidation(inst.validationDefault); console.log('[teardown] ' + inst.key + ' requireResultValidation=' + inst.validationDefault); }
    catch (e) { console.log('[teardown] ' + inst.key + ' COULD NOT RESTORE requireResultValidation: ' + e); }
    finally { await rest.dispose(); }
  }
}
