// Site settings. Run only against test instances with synthetic data: the tests create patients, orders and users,
// and change pihapps.labs.requireResultValidation while they run.

export interface Creds { username: string; password: string; }

/** A test user that global setup creates (with a provider) when it does not exist. */
export interface TestUser extends Creds { roles: string[]; }

export interface NumericTest {
  uuid: string;
  name: RegExp;          // as shown on the pages
  normal: string;
  abnormal: string;
  critical: string;
  invalid: string;
  minimum: string;       // the absolute minimum, as shown in "Minimum allowed value: X"
}

export interface Instance {
  key: 'sierra-leone' | 'rwanda' | 'haiti';
  baseURL: string;
  validationDefault: boolean;     // what the site runs; restored after every test that changes it
  testUsers: TestUser[];          // created by global setup
  users: {
    admin: Creds;
    tech: Creds;                  // enters results (no validate privilege on SL/Haiti)
    validator: Creds;             // validates
    validator2?: Creds;           // a second user able to validate (stale page cases)
    clinician: Creds;             // views results
    noPriv?: Creds;               // lab tech without the validate privilege, for E7
  };
  techHasValidatePrivilege: boolean;
  sessionLocation: string;
  orderLocation: string;
  orderEncounterType: string;
  orderEncounterRole: string;
  specimenEncounterType: string;
  careSetting?: string;           // looked up (Outpatient) when absent
  orderType: string;
  orderer?: string;               // provider uuid; looked up for admin when absent
  identifierType: string;
  identifierSource?: string;      // idgen source; else a random identifier
  labIdConcept: string;
  orderNumberConcept: string;
  numeric: NumericTest;           // a numeric test with normal/critical/absolute ranges
  numeric2: { uuid: string; name: RegExp; normal: string };  // another numeric test in the same category
  panel: { uuid: string; name: RegExp; members: { uuid: string; value: string; value2: string }[] };
  panelMemberAlone: string;       // a member of the panel that can be ordered on its own (A5)
  hasLabIdAction: boolean;        // "Enter results by Lab ID" patient-row action configured
  hasBacteriology: boolean;
  hasPrint: boolean;
}

const DEFAULT_URL = 'http://localhost:8080/openmrs';
/** E2E_<SITE>_URL, else E2E_URL, else http://localhost:8080/openmrs. */
function url(site: string) {
  return (process.env['E2E_' + site + '_URL'] ?? process.env.E2E_URL ?? DEFAULT_URL).replace(/\/+$/, '');
}

const PASSWORD = 'E2eTest123';
const admin: Creds = { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD ?? 'Admin123' };
const u = (username: string, ...roles: string[]): TestUser => ({ username, password: PASSWORD, roles });

const SL: Instance = {
  key: 'sierra-leone',
  baseURL: url('SIERRA_LEONE'),
  validationDefault: false,
  testUsers: [
    u('e2e_labtech', 'Application Role: labTech', 'Privilege Level: Full'),
    u('e2e_validator2', 'System Developer'),
  ],
  users: {
    admin,
    tech: u('e2e_labtech'),
    // No Sierra Leone role has "Task: pihapps.labs.validateResults": only admin (superuser) can validate
    validator: admin,
    validator2: u('e2e_validator2'),
    clinician: u('e2e_labtech'),
    noPriv: u('e2e_labtech'),
  },
  techHasValidatePrivilege: false,
  sessionLocation: '2bcb9215-8cd6-11eb-b7be-0242ac110002',
  orderLocation: '2bcb9215-8cd6-11eb-b7be-0242ac110002',
  orderEncounterType: 'b3a0e3ad-b80c-4f3f-9626-ace1ced7e2dd',
  orderEncounterRole: 'c458d78e-8374-4767-ad58-9f8fe276e01c',
  specimenEncounterType: '39C09928-0CAB-4DBA-8E48-39C631FA4286',
  careSetting: '6f0c9a92-6f24-11e3-af88-005056821db0',
  orderType: '52a447d3-a64a-11e3-9aeb-50e549534c5e',
  orderer: 'ff71087b-dac7-4a77-b58b-47a9e8fcf706',
  identifierType: 'c09a1d24-7162-11eb-8aa6-0242ac110002',
  identifierSource: '809b23e3-7162-11eb-8aa6-0242ac110002',
  labIdConcept: '162086AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
  orderNumberConcept: '393dec41-2fb5-428f-acfa-36ea85da6666',
  numeric: { uuid: '3ccc7158-26fe-102b-80cb-0017a47871b2', name: /Hemoglobin|HGB/i, normal: '13', abnormal: '25', critical: '5', invalid: '-1', minimum: '0' },
  numeric2: { uuid: '3cd69a98-26fe-102b-80cb-0017a47871b2', name: /Hematocrit|HCT/i, normal: '40' },
  panel: {
    uuid: '68da7da7-5b09-4689-921a-fc1e5402033f', name: /CHEM8/i,
    members: [
      { uuid: '3cd76b58-26fe-102b-80cb-0017a47871b2', value: '145', value2: '147' },   // sodium
      { uuid: '3cd76ce8-26fe-102b-80cb-0017a47871b2', value: '4.1', value2: '4.5' },   // potassium
    ],
  },
  panelMemberAlone: '3ccc7158-26fe-102b-80cb-0017a47871b2',   // Hemoglobin, a CHEM8+ member
  hasLabIdAction: false,
  hasBacteriology: false,
  hasPrint: false,
};

const RW: Instance = {
  key: 'rwanda',
  baseURL: url('RWANDA'),
  validationDefault: true,
  testUsers: [
    u('e2e_labtech', 'System Developer'),
    u('e2e_validator', 'System Developer'),
  ],
  users: {
    admin,
    tech: u('e2e_labtech'),
    validator: u('e2e_validator'),
    validator2: admin,
    clinician: admin,
  },
  techHasValidatePrivilege: true,   // both Rwanda test users are System Developer
  sessionLocation: '320a7c98-78f3-4e6b-9f0c-1a520eef44c1',
  orderLocation: '320a7c98-78f3-4e6b-9f0c-1a520eef44c1',
  orderEncounterType: '4d912ae2-26fe-102b-80cb-0017a47871b2',
  orderEncounterRole: '240b26f9-dd88-4172-823d-4a8bfeb7841f',
  specimenEncounterType: '082e1261-cbd6-48a1-9300-51f0ab9ef489',
  orderType: '52a447d3-a64a-11e3-9aeb-50e549534c5e',
  orderer: '50a86e5b-b3ae-4620-ae8f-d8af500f97e6',
  identifierType: 'b9e6d544-a1f7-4db6-a107-1f56c511d145',
  labIdConcept: '162086AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
  orderNumberConcept: '393dec41-2fb5-428f-acfa-36ea85da6666',
  // SERUM CREATININE: normal 10-120, critical <1 or >1000, absolute 0-2000
  numeric: { uuid: '3cd4374e-26fe-102b-80cb-0017a47871b2', name: /CREATININE/i, normal: '80', abnormal: '150', critical: '0.5', invalid: '-1', minimum: '0' },
  numeric2: { uuid: '3cd76ce8-26fe-102b-80cb-0017a47871b2', name: /POTASSIUM/i, normal: '4' },
  panel: {
    uuid: '3cd6a0e2-26fe-102b-80cb-0017a47871b2', name: /COMPLETE BLOOD COUNT/i,
    members: [
      { uuid: '3cd2a906-26fe-102b-80cb-0017a47871b2', value: '6', value2: '7' },        // WBC
      { uuid: '3cd69a98-26fe-102b-80cb-0017a47871b2', value: '40', value2: '42' },      // HCT
    ],
  },
  panelMemberAlone: '3ccc7158-26fe-102b-80cb-0017a47871b2',   // HEMOGLOBIN, a CBC member
  hasLabIdAction: true,
  hasBacteriology: true,
  hasPrint: true,
};

const HT: Instance = {
  key: 'haiti',
  baseURL: url('HAITI'),
  validationDefault: false,
  testUsers: [
    u('e2e_labtech', 'Application Role: labTech', 'Privilege Level: Full'),
    u('e2e_validator2', 'System Developer'),
  ],
  users: {
    admin,
    tech: u('e2e_labtech'),
    // As on Sierra Leone, no Haiti role has "Task: pihapps.labs.validateResults": only admin can validate
    validator: admin,
    validator2: u('e2e_validator2'),
    clinician: admin,
  },
  techHasValidatePrivilege: false,
  sessionLocation: '730ad64b-a2cd-40f7-91ac-ecc8fabf9f3e',   // CDI Klinik Eksten Jeneral Laboratwa
  orderLocation: '730ad64b-a2cd-40f7-91ac-ecc8fabf9f3e',
  orderEncounterType: 'b3a0e3ad-b80c-4f3f-9626-ace1ced7e2dd',
  orderEncounterRole: 'c458d78e-8374-4767-ad58-9f8fe276e01c',
  specimenEncounterType: '39C09928-0CAB-4DBA-8E48-39C631FA4286',
  careSetting: '6f0c9a92-6f24-11e3-af88-005056821db0',
  orderType: '52a447d3-a64a-11e3-9aeb-50e549534c5e',
  identifierType: 'a541af1e-105c-40bf-b345-ba1fd6a59b85',
  identifierSource: '52250ca2-d25f-11e4-8dbf-54ee7513a7ff',
  labIdConcept: '162086AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
  orderNumberConcept: '393dec41-2fb5-428f-acfa-36ea85da6666',
  numeric: { uuid: '3ccc7158-26fe-102b-80cb-0017a47871b2', name: /Hemoglobin|HGB/i, normal: '13', abnormal: '25', critical: '5', invalid: '-1', minimum: '0' },
  numeric2: { uuid: '3cd69a98-26fe-102b-80cb-0017a47871b2', name: /Hematocrit|HCT/i, normal: '40' },
  panel: {
    uuid: '3cecf068-26fe-102b-80cb-0017a47871b2', name: /Electrolytes/i,
    members: [
      { uuid: '3cd76b58-26fe-102b-80cb-0017a47871b2', value: '145', value2: '147' },   // sodium
      { uuid: '3cd76ce8-26fe-102b-80cb-0017a47871b2', value: '4.1', value2: '4.5' },   // potassium
    ],
  },
  panelMemberAlone: '3cd76ce8-26fe-102b-80cb-0017a47871b2',   // Serum potassium, an electrolytes panel member
  hasLabIdAction: false,
  hasBacteriology: false,
  hasPrint: false,
};

// LABID_ACTION=on|off says whether the "Enter results by Lab ID" patient action is configured on the instance
// (run-matrix.sh adds or removes it before the run). Unset: the site's own configuration.
const LABID_ACTION = process.env.LABID_ACTION;
for (const i of [SL, RW, HT]) {
  if (LABID_ACTION === 'on') i.hasLabIdAction = true;
  if (LABID_ACTION === 'off') i.hasLabIdAction = false;
}

export const INSTANCES: Record<string, Instance> = { 'sierra-leone': SL, 'rwanda': RW, 'haiti': HT };

/** The sites to run: E2E_SITES (comma separated), else all. */
export function selectedSites(): Instance[] {
  const keys = (process.env.E2E_SITES ?? Object.keys(INSTANCES).join(',')).split(',').map(k => k.trim()).filter(Boolean);
  for (const k of keys) if (!INSTANCES[k]) throw new Error('Unknown site in E2E_SITES: ' + k + ' (use ' + Object.keys(INSTANCES).join(', ') + ')');
  return keys.map(k => INSTANCES[k]);
}

export function instanceFor(projectName: string): Instance {
  const i = INSTANCES[projectName];
  if (!i) throw new Error('Unknown project ' + projectName);
  return i;
}
