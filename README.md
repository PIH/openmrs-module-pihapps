# openmrs-module-pihapps

Shared, configurable OpenMRS apps for PIH distributions

## Lab Workflows

This module provides a complete laboratory ordering and results workflow. The workflows are linked together and share configuration through global properties.

### Functional Overview

#### 1. Patient Lab Orders

**Page:** `pihapps/labs/labOrders`

Displays all lab orders for a specific patient. Orders can be filtered by test, date range, fulfillment status, and accession number. STAT orders are flagged with a red icon. Active orders can be discontinued from this page with an optional free-text reason. The page includes a link to place new orders for the patient.

#### 2. Order Lab Tests

**Page:** `pihapps/labs/labOrder`

Allows a clinician to place one or more lab orders for a patient in a single encounter. Tests are organized by category and can be toggled between ROUTINE and STAT urgency. For configured tests, an order reason can be selected. The orderer, location, and order date can be specified. Selected tests appear in a draft sidebar before the encounter is saved.

This page is also accessible via an `<pihLabOrder>` tag in HTMLFormEntry forms, which renders the same category-based test selection widget within a form.

#### 3. System-wide Lab Order List

**Page:** `pihapps/labs/labOrderList`

Provides a cross-patient view of all lab orders in the system. Automatically filters by the visit location associated with the current session location. Defaults to showing orders in "In Fulfillment" status. Filters include patient, test, date range, accession number, and fulfillment status.

From this page, a user can:
- Click a specimen date to view or edit the specimen collection encounter details
- Click an order's not-performed status to view or edit the non-fulfillment reason
- Click the pencil icon on a collected order to enter lab results
- Navigate to the patient reception workflow

#### 4. Patient Reception and Specimen Collection

**Pages:** `pihapps/labs/labPatientList` and `pihapps/labs/labPatientReception`

`labPatientList` shows a dashboard of patients who have pending lab orders, defaulting to "Awaiting Fulfillment" status. Selecting a patient opens `labPatientReception`.

`labPatientReception` shows all pending orders for a single patient and allows the user to select one or more orders for bulk specimen collection processing. The specimen collection encounter records the collection date, lab identifier, test location, and links the encounter to the selected orders. Orders can also be marked as not performed from this page with a configurable reason.

#### 5. Lab Results Entry

**Fragment:** `pihapps/labs/recordLabResults` (inline within `labOrderList`)

Accessible by clicking the pencil icon on an order that has a specimen collection encounter. Supports numeric (with configurable range validation), coded, text, and datetime result types. Requires a specimen collection encounter to exist before results can be entered. Records specimen received date, results date, and optionally a free-text comment alongside each result value (configurable via `pihapps.labs.collectResultComments`).

Concepts listed in `pihapps.labs.multipleAnswerConcepts` support recording more than one result observation per order (e.g., a Stool Exam that produces several organism findings). For these concepts the entry form shows a dynamic list of input rows with add and remove controls. Entry order is preserved across saves via a numeric index appended to each obs's `formNamespaceAndPath`.

#### 6. Patient Lab Results

**Page:** `pihapps/labs/patientLabResults`

Displays historical lab results for a single patient. Results can be filtered by test category, specific test, and date range. Clicking a result value opens the `patientLabTrends` fragment, which shows a time-series chart of that test's values using Chart.js alongside a tabular history. For multiple-answer concepts, each result observation is shown as its own row; the trends chart is suppressed for these concepts but the tabular history is shown.

#### 7. Supporting Actions

Several supporting actions are available across the workflows:

- **Discontinue order** — available from the patient lab orders page for any active order; creates a DISCONTINUE order action via the standard OpenMRS encounter/order REST API
- **Mark as not performed** — available from patient reception and the lab order list; sets the order's fulfiller status to EXCEPTION and records a reason obs linked to the order's encounter
- **Specimen collection encounter** — records collection date, lab identifier, test location, and estimated collection date when applicable; links the encounter to the fulfilled orders via the `encounterFulfillingOrders` resource

---

### Configuration

All configuration is managed through OpenMRS global properties and is resolved in `LabOrderConfig`. Where properties have been renamed or migrated from legacy modules (labworkflowowa, orderentryowa, laboratorymanagement), the old property names are still supported as fallbacks.

| Property | Description | Default / Fallback |
|---|---|---|
| `orderentryowa.labOrderablesConceptSet` | Concept set defining available lab tests organized by category | required |
| `orderentryowa.orderReasonsMap` | Comma-separated `conceptRef=reasonSetRef` pairs mapping orderables to reason sets | optional |
| `pihapps.labs.labOrderEncounterType` | Encounter type for lab order encounters | `orderentryowa.encounterType` |
| `pihapps.labs.labOrderEncounterRole` | Encounter role for the ordering provider | `orderentryowa.encounterRole` |
| `pihapps.labs.autoExpireTimeInDays` | Days until lab orders auto-expire | `orderentryowa.labOrderAutoExpireTimeInDays`, default 30 |
| `pihapps.labs.labOrderType` | Order type UUID/ID for lab test orders | `labworkflowowa.testOrderType`, `laboratorymanagement.orderType.labOrderTypeId`, core TEST_ORDER_TYPE_UUID |
| `pihapps.labs.labResultCategoriesConceptSet` | Concept set defining categories for lab results display | `labworkflowowa.labCategoriesConceptSet` |
| `pihapps.labs.specimenCollectionEncounterType` | Encounter type for specimen collection encounters | `labworkflowowa.labResultsEntryEncounterType` |
| `pihapps.labs.specimenCollectionEncounterRole` | Encounter role for specimen collection encounters | optional |
| `pihapps.labs.estimatedCollectionDateQuestion` | Concept for the estimated collection date obs | `labworkflowowa.estimatedCollectionDateQuestion` |
| `pihapps.labs.estimatedCollectionDateAnswer` | Coded answer concept for "estimated" collection date | `labworkflowowa.estimatedCollectionDateAnswer` |
| `pihapps.labs.testOrderNumberConcept` | Concept for recording the test order number in the specimen encounter | `labworkflowowa.testOrderNumberConcept` |
| `pihapps.labs.labIdentifierConcept` | Concept for recording the specimen/lab identifier | default `CIEL:162086` |
| `pihapps.labs.locationOfLaboratory` | Concept for recording which laboratory is performing the test | `labworkflowowa.locationOfLaboratory` |
| `pihapps.labs.specimenReceivedDateConcept` | Concept for the specimen received date in results entry | default `PIH:21057` |
| `pihapps.labs.resultsDateConcept` | Concept for the results date in results entry | default `PIH:10783` |
| `pihapps.labs.didNotPerformReason` | Concept for the reason a test was not performed | `labworkflowowa.didNotPerformReason` |
| `pihapps.labs.collectResultComments` | Whether to show a free-text comment field alongside result values | default `true` |
| `pihapps.labs.multipleAnswerConcepts` | Comma-delimited concept references (UUID, source:code, or name) for tests that can produce multiple result observations per order | `laboratorymanagement.multipleAnswerConceptIds` |
| `pihapps.labs.conceptDisplayFormat` | Format string controlling how lab concept names are displayed | optional |
| `laboratorymanagement.currentLabRequestFormConceptIDs` | Legacy: restricts the list of orderable tests to a specific subset | optional |
| `coreapps.dashboardUrl` | URL template for patient dashboard links | `/coreapps/clinicianfacing/patient.page?patientId={{patientId}}` |