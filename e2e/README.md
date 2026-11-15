# pihapps end-to-end tests

Preliminary Playwright suite for the pihapps lab features: results entry, optional result validation, the Lab ID
page, and the result editor/renderer plug-in points (with Rwanda's bacteriology editor). It runs against a running
OpenMRS server of a site that uses pihapps: Sierra Leone, Rwanda or Haiti. How these tests are packaged and run by
each distribution is being worked out in UHM-9672; expect this layout to change.

**Run it only against test servers with synthetic data.** The tests create patients, orders and users, and switch
the global property `pihapps.labs.requireResultValidation` while they run (global teardown restores the site's
default).

## Layout

| Path | Contents |
|---|---|
| `TEST-CASES.md` | The test cases; each test in `specs/` is named after its case id |
| `specs/` | One file per section: A entry (validation off), B Lab ID page, C–E validation on, F bacteriology (Rwanda), G Rwanda configuration, I stale pages, J validation off |
| `lib/instances.ts` | Per-site settings: URL, users per role, concepts and metadata to test with, which features the site has configured |
| `lib/global-setup.ts` | Checks the server is the expected site, creates the test users and providers, sets the site's validation default |
| `lib/rest.ts`, `lib/pages.ts`, `lib/fixtures.ts` | REST helpers (patients, orders, specimen collection, stored obs and events), page helpers, fixtures (`withValidation(on)`) |
| `run-matrix.sh` | Runs a site with the "Enter results by Lab ID" action configured, then removed (needs a restart each time) |

Sections that do not apply to a site are skipped (e.g. F and G outside Rwanda). Case H (migration at startup) is not
automated.

## Prerequisites

- Node.js 20 or later, then in this directory:

  ```
  npm ci
  npx playwright install chromium
  ```

- A running server for the site, built with the pihapps (and, for Rwanda, rwandaemr) version under test, and an
  `admin` user (password `Admin123`, or set `E2E_ADMIN_PASSWORD`).
  - **Sierra Leone**: the pihsl-emr distribution with the `partnersinhealth/pihsl-emr-seed-sierraleone` seed, e.g.
    with `pihx` (openmrs-contrib-distro-tools). Config `sierraLeone,sierraLeone-kgh,sierraLeone-kgh-ci`.
  - **Haiti**: the zl-emr distribution with the `partnersinhealth/zl-emr-seed-haiti` seed, as in zl-emr's
    smoke-test workflow.
  - **Rwanda**: there is no seed image yet. The suite was run against the Butaro configuration of
    openmrs-distro-rwandaemr on a metadata-only database. On builds from rwandaemr `RHIP-INTEGRATION`, logins can
    fail after a restart unless Anonymous has the `Get Global Properties` privilege (a known rwandaemr issue).
    The bacteriology concepts must be loaded and the exams added to `laboratorymanagement.currentLabRequestFormConceptIDs`.
- The concepts, locations and other metadata the tests use are listed per site in `lib/instances.ts`.

## Running

Every site defaults to `http://localhost:8080/openmrs`, so run the site that is up:

```
E2E_SITES=sierra-leone npx playwright test
E2E_SITES=rwanda npx playwright test specs/c-e-validation-on.spec.ts     # one file
E2E_SITES=haiti npx playwright test -g "B3"                               # tests whose name matches
E2E_SITES=rwanda npx playwright test --ui                                 # interactive UI mode
```

Global setup stops the run if the server at the URL does not look like the site (its session location is missing).

| Variable | Meaning | Default |
|---|---|---|
| `E2E_SITES` | Sites to run, comma separated: `sierra-leone`, `rwanda`, `haiti` | all three |
| `E2E_URL` | Server URL for every site | `http://localhost:8080/openmrs` |
| `E2E_SIERRA_LEONE_URL`, `E2E_RWANDA_URL`, `E2E_HAITI_URL` | URL for one site, overriding `E2E_URL` | |
| `E2E_ADMIN_PASSWORD` | Password of `admin` | `Admin123` |
| `LABID_ACTION` | `on`/`off`: whether the "Enter results by Lab ID" patient action is configured; unset means the site's own configuration | |

To run several sites in one go, start them on different ports and set the per-site URLs:

```
E2E_SIERRA_LEONE_URL=http://localhost:8080/openmrs E2E_HAITI_URL=http://localhost:8081/openmrs \
  E2E_SITES=sierra-leone,haiti npx playwright test
```

### Lab ID action on and off

The "Enter results by Lab ID" patient action is appframework configuration, read at startup. `run-matrix.sh` adds or
removes it in the server's `openmrs_config/appframework` directory, restarts the server, and runs the suite each way:

```
E2E_SITES=haiti E2E_APPFRAMEWORK_DIR=/path/to/openmrs_config/appframework \
  E2E_RESTART_CMD='docker restart my-openmrs' ./run-matrix.sh
```

The reports go to `playwright-report-labid-on` and `playwright-report-labid-off`. The directory is restored at the end.

## Results

```
npx playwright show-report                       # or playwright-report-labid-on / -off
npx playwright show-trace test-results/<test>/trace.zip
```

Every test records a trace and screenshots; the report links to them. The trace viewer steps through each action with
the page before and after, the network calls and the console.

## Users

Global setup creates these when missing (password `E2eTest123`), each with a provider:

| Site | Users |
|---|---|
| Sierra Leone, Haiti | `e2e_labtech` (labTech), `e2e_validator2` (System Developer, a second validator for the stale page cases) |
| Rwanda | `e2e_labtech`, `e2e_validator` (both System Developer, so separation of duties is what stops self-validation) |

On Sierra Leone and Haiti no role has `Task: pihapps.labs.validateResults`, so `admin` validates.
