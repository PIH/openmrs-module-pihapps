# Lab Module — Technical Reference

This document covers the internal architecture of the lab workflows in `openmrs-module-pihapps`. It is intended as a working reference for developers who need to understand how the pieces fit together without re-reading every file from scratch.

---

## Module Structure

```
api/src/main/java/org/openmrs/module/pihapps/
  PihAppsConfig.java                     # Spring component; top-level config facade
  PihAppsService.java                    # Service interface for all lab data operations
  orders/
    LabOrderConfig.java                  # All lab configuration resolved from global properties
    LabTestCategory.java                 # Domain object: a category concept + its test concepts
    OrderSearchCriteria.java             # Search parameters for getOrders / getPatientsWithOrders
    OrderSearchResult.java               # Paginated result wrapper for orders
    OrderStatus.java                     # Enum: ACTIVE, EXPIRED, STOPPED
    OrderFulfillmentStatus.java          # Enum combining order+fulfiller status for UI filtering
    PatientWithOrders.java               # Patient + their list of orders
    PatientWithOrdersSearchResult.java   # Paginated result wrapper for patient-grouped orders
    EncounterFulfillingOrders.java       # Links a specimen collection encounter to its orders

omod/src/main/java/org/openmrs/module/pihapps/
  rest/
    LabOrderRestController.java          # GET /labOrder, GET /patientsWithOrders, POST /markOrdersAsNotPerformed
    LabResultsRestController.java        # GET /labResults
    EncounterFulfillingOrdersResource.java  # REST resource: GET/POST /encounterFulfillingOrders
    PihAppsConfigRestController.java     # GET /config — serializes PihAppsConfig to JSON
    OrderWithFulfillerDetailsResource.java  # Extends order resource with fulfillerEncounter
    ExtendedConceptResource.java         # Extends concept resource with displayStringForLab
  page/controller/labs/
    LabOrderPageController.java          # labOrder.gsp — injects test categories + order reasons
    LabOrdersPageController.java         # labOrders.gsp — injects patient wrapper
    LabOrderListPageController.java      # labOrderList.gsp — injects session/visit location
    LabPatientListPageController.java    # labPatientList.gsp — injects location + default status
    LabPatientReceptionPageController.java  # labPatientReception.gsp — injects patient + config
    PatientLabResultsPageController.java # patientLabResults.gsp — injects patient + config
  htmlformentry/labs/
    LabOrderTagHandler.java              # Processes <pihLabOrder> tag
    LabOrderWidget.java                  # Renders category/test/reason widget in forms
    LabOrderWidgetConfig.java            # Holds widget configuration (concepts→categories/reasons)

omod/src/main/webapp/
  pages/labs/
    labOrders.gsp                        # Patient-specific order history
    labOrder.gsp                         # Order entry page
    labOrderList.gsp                     # System-wide order list + inline forms
    labPatientList.gsp                   # Patient dashboard (grouped by patient)
    labPatientReception.gsp              # Per-patient specimen collection
    patientLabResults.gsp                # Patient result history
  fragments/labs/
    specimenCollectionEncounter.gsp      # Specimen collection form (inline in multiple pages)
    recordLabResults.gsp                 # Results entry form (inline in labOrderList)
    recordOrderNotFulfilled.gsp          # Not-performed reason form (inline in multiple pages)
    selectedOrders.gsp                   # Read-only order display used within forms
    patientLabTrends.gsp                 # Chart.js trend chart (inline in patientLabResults)
  resources/scripts/labs/
    renderLabOrdersByCategory.js         # HTMLFormEntry widget renderer
  resources/styles/labs/
    labs.css, labOrder.css, orderEntry.css, renderLabOrdersByCategory.css
```

---

## Configuration Architecture

All configuration lives in `LabOrderConfig` (api), which is a plain Spring-managed bean (not a `@Component`; wired manually via `applicationContext-service.xml` or equivalent). `PihAppsConfig` is the top-level `@Component` that holds a reference to `LabOrderConfig` and exposes display-formatting settings (date formats, locale, primary identifier type).

`LabOrderConfig` reads global properties through `ConfigUtil.getGlobalProperty()` / `ConfigUtil.getProperty()`. Many properties have fallbacks to legacy property names from earlier modules:

```
pihapps.labs.X  →  labworkflowowa.X  →  orderentryowa.X  →  core default
```

The frontend fetches config via `GET /rest/v1/pihapps/config?v=custom:(...)`. The custom representation syntax allows callers to request exactly the fields they need, which keeps payloads small. Every page/fragment fetches this endpoint at load time rather than receiving config from the server-side controller.

### Key configuration methods in `LabOrderConfig`

| Method | Property | Purpose |
|---|---|---|
| `getLabOrderablesConceptSet()` | `orderentryowa.labOrderablesConceptSet` | Root concept set; its members are categories, each category's members are orderable tests |
| `getAvailableLabTestsByCategory()` | — | Walks the concept set; optionally filters by `laboratorymanagement.currentLabRequestFormConceptIDs` |
| `getOrderReasonsMap()` | `orderentryowa.orderReasonsMap` | Returns `Map<Concept, List<Concept>>`; reason concepts resolved from set members or answers |
| `getLabTestOrderType()` | `pihapps.labs.labOrderType` | Falls back through labworkflowowa, laboratorymanagement, then core UUID |
| `getTestOrderTypes()` | — | All order types whose Java class is assignable from `TestOrder` |
| `getLabOrderEncounterType/Role()` | `pihapps.labs.labOrderEncounterType/Role` | Used when placing/discontinuing orders |
| `getSpecimenCollectionEncounterType/Role()` | `pihapps.labs.specimenCollectionEncounterType/Role` | Used when saving specimen collection encounters |
| `getCollectResultComments()` | `pihapps.labs.collectResultComments` | Defaults `true`; controls comment field in results entry |
| `getMultipleAnswerConceptsReference()` | `pihapps.labs.multipleAnswerConcepts` | Falls back to `laboratorymanagement.multipleAnswerConceptIds`; returns raw property string |
| `getMultipleAnswerConcepts()` | — | Resolves the reference string to `List<Concept>`; result is cached and invalidated when the property value changes |

---

## Status Model

Understanding how order status is represented is essential for working with the filtering and display logic.

### `OrderStatus` (api enum)
Three values mapped from OpenMRS core order state:
- `ACTIVE` — order is current (not stopped/expired/voided)
- `EXPIRED` — past auto-expire date
- `STOPPED` — explicitly discontinued

### `Order.FulfillerStatus` (OpenMRS core enum)
- `null` — no action taken yet (specimen not yet collected)
- `RECEIVED` — specimen received / collected (set by specimen collection encounter)
- `IN_PROGRESS` — lab is working on it
- `ON_HOLD`
- `COMPLETED` — results entered
- `EXCEPTION` — not performed / could not complete
- `DECLINED`

### `OrderFulfillmentStatus` (api enum)
A composite status that combines `OrderStatus` + `FulfillerStatus` into user-facing filter values:

| Enum value | OrderStatus | FulfillerStatuses | Include null fulfiller? |
|---|---|---|---|
| `AWAITING_FULFILLMENT` | ACTIVE | RECEIVED | yes (not yet collected) |
| `IN_FULFILLMENT` | — | IN_PROGRESS, ON_HOLD | — |
| `COMPLETED_FULFILLMENT` | — | COMPLETED | — |
| `UNABLE_TO_COMPLETE_FULFILLMENT` | — | EXCEPTION, DECLINED | — |
| `EXPIRED_BEFORE_FULFILLMENT` | EXPIRED | RECEIVED | yes |
| `CANCELLED_BEFORE_FULFILLMENT` | STOPPED | RECEIVED | yes |

Note: `AWAITING_FULFILLMENT` includes both null fulfiller status (never collected) and `RECEIVED` status (specimen collected but fulfiller not yet updated), because specimen collection sets the fulfiller status to `RECEIVED`.

---

## REST API

All endpoints are under `/openmrs/ws/rest/v1/pihapps/`.

### `GET /labOrder`

Searches orders with pagination. Delegates to `PihAppsService.getOrders(OrderSearchCriteria)`.

| Parameter | Type | Notes |
|---|---|---|
| `patient` | UUID | Patient filter |
| `labTest` | UUID | Concept filter |
| `orderType` | UUID (repeatable) | If omitted, defaults to all `TestOrder` subtypes |
| `orderLocation` | UUID (repeatable) | Filters by order encounter location |
| `activatedOnOrAfter` | `yyyy-MM-dd` | |
| `activatedOnOrBefore` | `yyyy-MM-dd` | |
| `accessionNumber` | string | |
| `orderFulfillmentStatus` | `OrderFulfillmentStatus` enum name | |
| `sortBy` | `field-DIRECTION` (repeatable) | e.g. `dateActivated-desc` |
| `v` | custom representation | Standard OpenMRS REST `v` param |
| `startIndex`, `limit` | int | Pagination |

Returns a standard paged result. The response includes `results` and `totalCount`.

The `Order` representation used by the pages requests additional computed properties including `fulfillerEncounter` (from `OrderWithFulfillerDetailsResource`) and `reasonOrderNotFulfilled`.

### `GET /patientsWithOrders`

Same parameters as `/labOrder`. Returns patients grouped with their matching orders. Delegates to `PihAppsService.getPatientsWithOrders()`.

### `POST /markOrdersAsNotPerformed`

Body:
```json
{
  "orders": ["order-uuid-1", "order-uuid-2"],
  "reason": "concept-uuid-or-reference"
}
```

Sets fulfiller status to `EXCEPTION` on each order and records a reason obs linked to the order encounter. Returns HTTP 204 on success.

### `GET /labResults`

| Parameter | Type | Notes |
|---|---|---|
| `patient` | UUID | |
| `labTest` | UUID | Walks concept set members recursively |
| `category` | UUID | Walks concept set members recursively |
| `onOrAfter`, `onOrBefore` | `yyyy-MM-dd` | |
| `sortBy` | `field-DIRECTION` | Defaults to `obsDatetime-DESC, obsId-DESC` |

If neither `labTest` nor `category` is supplied, uses `labResultCategoriesConceptSet` to find all result concept leaves.

### `GET /config`

Returns serialized `PihAppsConfig` as JSON. Uses custom representation syntax. Every page fetches this at load time; the custom `v` representation controls which fields are included. Key sub-representations:

- `labOrderConfig.availableLabTestsByCategory` — categories + tests for order/filter dropdowns
- `labOrderConfig.orderFulfillmentStatusOptions` — display labels for status filter dropdowns
- `labOrderConfig.specimenCollectionEncounterType`, `specimenCollectionEncounterRole` — used when creating specimen encounters
- `labOrderConfig.collectResultComments` — boolean; toggles comment field in results form

### `GET /encounterFulfillingOrders/{encounterUuid}`
### `POST /encounterFulfillingOrders`

CRUD resource for `EncounterFulfillingOrders`. POST creates a specimen collection encounter linked to the specified orders. The `save()` implementation delegates to `PihAppsService.saveEncounterFulfillingOrders()`.

---

## Frontend Architecture

All pages follow the same pattern: the Groovy page controller injects minimal server-side state (patient wrapper, session location), and all data is fetched at runtime via REST calls in JavaScript.

### Shared JavaScript utilities

- `pagingDataTable.js` — wraps DataTables with a custom paging model; used on all tabular order/results pages
- `patientUtils.js` — helpers for extracting preferred identifier, computing order status display values
- `dateUtils.js` — locale-aware date formatting using moment.js
- `formHelper.js` — used by specimen collection and results forms to build OpenMRS encounter/obs payloads

### Page interactions

**labOrders.gsp** — On load, fetches `/config` for display options and populates filter dropdowns, then loads `/labOrder` with patient filter. Filter changes trigger a table reload. Discontinue action posts a DISCONTINUE order action to the core `/encounter` endpoint.

**labOrderList.gsp** — More complex. Three sections toggled in/out: the order table (`#view-orders-section`), specimen encounter edit (`#edit-specimen-encounter-section`), and results entry (`#record-lab-results-section`). Each section contains an included fragment whose JavaScript function (`initializeSpecimenCollectionForm`, `initializeLabResultsForm`, `initializeOrderNotFulfilledForm`) is called with the relevant data and a callback to refresh the table on save.

**labPatientReception.gsp** — Loads `/labOrder` with patient + `AWAITING_FULFILLMENT` status. Checkboxes select orders; the specimen collection fragment is initialized with the selected orders when the user proceeds.

**patientLabResults.gsp** — Loads `/labResults` for the patient. Clicking a result value calls `initializeLabTrends()` which loads Chart.js with historical data for that concept.

### `renderLabOrdersByCategory.js`

Used only in HTMLFormEntry contexts (`<pihLabOrder>` tag). Renders the category/test checkbox UI and generates hidden form fields for the HTMLFormEntry order widget. Handles:
- Collapsible category sections
- Panel tooltips showing component tests
- Urgency toggle (ROUTINE ↔ STAT) per test
- Pre-population from existing orders in edit mode (generates DISCONTINUE actions for removed orders)
- Order reason dropdowns per configured test

---

## Service Layer

`PihAppsService` (interface in api) is implemented in the api module. Key methods:

```java
OrderSearchResult getOrders(OrderSearchCriteria criteria)
PatientWithOrdersSearchResult getPatientsWithOrders(OrderSearchCriteria criteria)
EncounterFulfillingOrders saveEncounterFulfillingOrders(EncounterFulfillingOrders efs)
EncounterFulfillingOrders getEncounterFulfillingOrders(String encounterUuid)
Encounter getFulfillerEncounterForOrder(Order order)
Obs getReasonOrderNotFulfilled(Order order)
void markOrdersAsNotFulfilled(List<Order> orders, Concept reason)
ObsSearchResult getObs(ObsSearchCriteria criteria)
```

`OrderSearchCriteria` fields map directly to the REST parameters above. `startIndex`/`limit` are passed through for database-level pagination; `sortCriteria` is a list of `SortCriteria(field, ASC|DESC)`.

---

## HTMLFormEntry Integration

The `<pihLabOrder>` tag in HTML forms uses the standard OpenMRS `OrderTagHandler` extension point. `LabOrderTagHandler` configures defaults (OUTPATIENT care setting, ROUTINE/STAT urgency options) and delegates to `LabOrderWidget` for rendering.

`LabOrderWidget.generateHtml()` serializes the full category/test/reason configuration into JavaScript variables that `renderLabOrdersByCategory.js` reads to build the UI. The generated hidden inputs follow HTMLFormEntry's order widget naming conventions and are processed server-side by the standard order form submission handler.

---

## Extended REST Resources

Two custom resource wrappers add fields not available in core representations:

**`OrderWithFulfillerDetailsResource`** — Adds `fulfillerEncounter` (the specimen collection encounter linked to this order) and `reasonOrderNotFulfilled` (the not-performed reason obs) to the default order representation. This allows the order list pages to show specimen date links and not-performed reason links without additional round trips.

**`ExtendedConceptResource`** — Adds two properties to the concept representation:

- `displayStringForLab` — applies the `pihapps.labs.conceptDisplayFormat` global property, allowing sites to control how concept names are displayed in lab UIs independently of the concept's default display.
- `multipleAnswer` — boolean; `true` when the concept appears in `LabOrderConfig.getMultipleAnswerConcepts()`. This flag travels with every concept in REST responses and is read by the results entry form, the results display page, and the trends fragment to switch between single- and multi-value behaviour.

---

## Data Flow: Specimen Collection

1. User selects one or more orders in `labPatientReception.gsp` or `labPatientList.gsp`
2. Frontend calls `initializeSpecimenCollectionForm({ patientUuid, orders, encounter, pihAppsConfig, onSuccessFunction })`
3. Form builds an encounter payload with:
   - `encounterType`: `specimenCollectionEncounterType`
   - `encounterRole`: `specimenCollectionEncounterRole`
   - Obs for: lab identifier (`labIdentifierConcept`), test location (`testLocationQuestion`), estimated collection date if applicable, specimen received date, test order number
4. If creating a new encounter, POSTs to core `/ws/rest/v1/encounter`
5. Then POSTs to `/ws/rest/v1/encounterFulfillingOrders` with `{ encounter: uuid, orders: [uuid, ...] }`, which:
   - Sets `fulfillerStatus = RECEIVED` on each order
   - Persists the encounter↔orders association in `EncounterFulfillingOrders`

## Data Flow: Results Entry

1. User clicks pencil icon on an order in `labOrderList.gsp`
2. Frontend calls `initializeLabResultsForm({ order, pihAppsConfig, onSuccessFunction })`
3. Requires `order.fulfillerEncounter` to be non-null (specimen must be collected first)
4. Fetches existing obs from the fulfiller encounter
5. User enters result values; form validates ranges if `allowDecimal` / reference range is set
6. On save, PATCHes or POSTs obs on the fulfiller encounter:
   - Sets `fulfillerStatus = COMPLETED` on the order
   - Records `specimenReceivedDateQuestion` and `resultsDateQuestion` obs
   - Optionally records `comment` on the obs if `collectResultComments = true`

### Multiple-answer concepts

When `order.concept.multipleAnswer` is `true`, `recordLabResults.gsp` renders a dynamic row list instead of a single widget:

- Existing obs are loaded via `FormHelper.getInitialObsValues()`, sorted by the numeric suffix in their `formNamespaceAndPath` to reconstruct original entry order.
- Each row passes `initialObsUuid` to `FormHelper.createObsWidget()` so the correct existing obs is pre-filled.
- The **Add another result** button appends a new empty row. New rows receive a `pathIndex` (next integer after the highest existing index), which is appended to `data-form-path` (`/conceptUuid/N`) so `constructEncounterPayload` builds a correctly indexed `formNamespaceAndPath`.
- Removing a row clears its `result-value-field` before removing the element from the DOM. The cleared widget field remains in `FormHelper.obsWidgetFields` (a plain array, not a live DOM query), so `constructEncounterPayload` still sees `obsUuid && !value` and sets `voided: true` on the obs.
- For **coded** multi-value concepts, a `syncOptions` function fires after every select change, row addition, and row removal. It disables and hides any option in a row's dropdown that is already selected in another row, and restores it if that row is cleared or removed. This prevents two rows from submitting the same coded answer.

### `FormHelper` multi-value methods

| Method | Purpose |
|---|---|
| `getInitialObsValues(conceptUuid)` | Returns all initial obs for a concept, sorted by `formNamespaceAndPath` index suffix |
| `getNextPathIndex(conceptUuid)` | Returns `max(existing path indices) + 1`, or `0` if none have an index suffix |
| `_pathIndex(formNamespaceAndPath)` | Extracts the numeric suffix from a path; returns `-1` if absent |
