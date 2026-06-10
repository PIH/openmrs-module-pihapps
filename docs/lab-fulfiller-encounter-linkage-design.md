# Lab Fulfiller Encounter Linkage — Design Document

This document describes the problem of linking lab orders to their fulfiller (specimen collection)
encounters, the legacy state across three systems, the proposed design for pihapps, and why the
design is compatible with both legacy systems without requiring data migration.

---

## Background

In OpenMRS, a `TestOrder` represents a lab order placed by a clinician. The fulfiller encounter
is the encounter in which specimen collection occurs and results are eventually recorded. There is
no native OpenMRS mechanism for storing this order-to-encounter relationship — it has to be
modeled at the application layer.

pihapps uses the fulfiller encounter for several things:
- Determining whether a specimen has been received for an order
- Deriving `OrderFulfillmentStatus` (AWAITING, IN_PROGRESS, COMPLETED, EXCEPTION)
- Displaying specimen metadata (received date, lab ID, location) alongside orders
- Navigating to the results entry form for a given order

The relationship needs to be queryable in both directions:
- Given an order → find its fulfiller encounter
- Given an encounter → find which orders it fulfills

---

## Legacy State

### laboratorymanagement-v2 (Rwanda EMR)

There is no specimen collection encounter. Orders and their result obs both live in the same
encounter (`LAB TEST` encounter type). Result obs have `obs.order_id` set to the order they
belong to, indexed in the database as `KEY obs_order (order_id)`.

No testOrderNumber obs. No explicit order-to-encounter linkage record of any kind. The
encounter is found implicitly from any obs linked to the order via `obs.order_id`.

**Fulfiller status** is never set by this module. The `MarkOrderAsFulfilledEventHandler` in
`openmrs-module-rwandaemr` handles this by setting `FulfillerStatus.COMPLETED` on the
`TestOrder` when result obs are created.

### labworkflow OWA (pihemr distro)

A single `Lab Results` encounter holds specimen metadata and results. The encounter is linked
to the order by a `testOrderNumberConcept` obs whose `valueText` is `order.orderNumber`.

Critically, this testOrderNumber obs also has `obs.order_id` set. This happens because the
labworkflow results entry form passes `orderForObs={selectedOrder}` to `EncounterFormPanel`,
and the react-components form saga applies `obs.order = orderUuid` to **all** obs submitted
via the form, including the testOrderNumber obs (confirmed in
`openmrs-react-components/src/features/form/sagas.js` line 98 and
`EncounterForm.jsx` lines 63–72).

Result obs also have `obs.order_id` set via the same mechanism.

**Fulfiller status** is set after results entry by the labworkflow `saveFulfillerStatus` saga:
EXCEPTION if a "did not perform" obs is present, COMPLETED if result obs are present,
IN_PROGRESS if the encounter exists but has no result obs.

### pihapps (current, pre-redesign)

A single `Laboratory Encounter` holds specimen metadata and results, created in two phases:
first at specimen reception (metadata obs written, fulfillerStatus set to IN_PROGRESS), then
results added to the same encounter later (fulfillerStatus set to COMPLETED).

The encounter is linked to the order by a `testOrderNumberConcept` obs written at specimen
collection time. However, **pihapps does not set `obs.order_id` on the testOrderNumber obs**
(confirmed in `specimenCollectionEncounter.gsp` lines 168–174 — only `concept`, `valueText`,
and `formNamespaceAndPath` are set; no `order` field).

`getFulfillerEncounterForOrder()` queries by `valueText = order.orderNumber` — a string match
on an unindexed column, narrowed by `concept_id` and `person_id`.

Result obs have `obs.order_id` set (via the standard encounter/obs REST endpoint).

---

## Problems with the Current Approach

1. **Unindexed string match.** The testOrderNumber lookup queries `obs.value_text = orderNumber`
   after narrowing by concept and patient. `value_text` is not indexed. This is the most
   expensive possible way to find the encounter.

2. **No obs.order_id on the testOrderNumber obs.** pihapps writes the testOrderNumber obs without
   setting `obs.order_id`, so the indexed FK path is not usable for the testOrderNumber obs in
   pihapps encounters.

3. **Semantically wrong.** The testOrderNumber obs is not a clinical observation — it is a
   system linkage record stored as an obs because there is no other convenient place.

4. **Pre-results window not covered by obs.order_id.** Before results are entered, the only obs
   in the specimen collection encounter with `obs.order_id` set would be the testOrderNumber obs
   — but only if we set `obs.order_id` on it, which pihapps currently does not.

5. **N+1 query pattern.** The lab order list page calls `getFulfillerEncounterForOrder()` once
   per order, producing N separate queries for a page of N orders.

---

## Proposed Design

### Fulfiller Status Obs

Replace the testOrderNumber obs as the primary linkage mechanism with a dedicated
**fulfiller status obs**, written per order when the specimen collection encounter is created.

Structure of each fulfiller status obs:
- `obs.encounter_id` = the specimen collection encounter
- `obs.order_id` = the order being fulfilled
- `obs.concept` = a status concept (see concept options below)
- `obs.value` = the current status (coded or text depending on chosen concept)
- `obs.obsDatetime` = timestamp of the status transition

This obs is written by pihapps at specimen collection time (one per order), replacing the
testOrderNumber obs for linkage purposes. When status changes (e.g. IN_PROGRESS → COMPLETED),
a new obs is created rather than voiding the previous one, providing a full history of status
transitions per order.

### Concept Options for the Status Obs

Two options remain, differing primarily on how strictly typed the value needs to be.

**Option A: Coded obs using an existing PIH question concept**

OpenMRS does not enforce which concepts may be used as coded answers, so the answer set is
not a constraint. The existing PIH concepts "Completed", "In Progress", and "Not Done"
(already associated with "Test Status") cover three of the four FulfillerStatus values:

| FulfillerStatus | Coded answer concept | GP |
|---|---|---|
| IN_PROGRESS | "In Progress" | `pihapps.labs.fulfillerStatusConcept.inProgress` |
| COMPLETED | "Completed" | `pihapps.labs.fulfillerStatusConcept.completed` |
| EXCEPTION | "Not Done" | `pihapps.labs.fulfillerStatusConcept.exception` |
| RECEIVED / null | "Pending" or "None" (TBD from PIH dictionary) | `pihapps.labs.fulfillerStatusConcept.received` |

The fourth answer concept (for RECEIVED/null — specimen received, awaiting results)
needs to be confirmed from the PIH dictionary. "Pending" is semantically accurate; "None"
is less descriptive. Whichever exists (or is added) can be used.

Two candidate question concepts:
- **"Fulfillment Status"** — already used in PIH for referral order workflow; semantically
  the best fit since it already represents order fulfillment state.
- **"Test Status"** — a Finding/Coded concept; slightly less precise semantically but
  already has "Completed", "In Progress", and "Not Done" associated.

"Fulfillment Status" is preferred. Either works since answer sets are not enforced.

The question concept UUID is configured via `pihapps.labs.fulfillerStatusConcept`.

**Enum-to-concept mapping in code**

`LabOrderConfig` exposes one getter per FulfillerStatus value, each backed by the
corresponding GP. The service uses two mapping methods:

```java
// Writing an obs: FulfillerStatus → Concept
Concept getConceptForFulfillerStatus(Order.FulfillerStatus status) {
    switch (status) {
        case IN_PROGRESS: return labOrderConfig.getFulfillerStatusInProgressConcept();
        case COMPLETED:   return labOrderConfig.getFulfillerStatusCompletedConcept();
        case EXCEPTION:   return labOrderConfig.getFulfillerStatusExceptionConcept();
        case RECEIVED:    return labOrderConfig.getFulfillerStatusReceivedConcept();
        default: return null;
    }
}

// Reading history: Concept → FulfillerStatus
Order.FulfillerStatus getFulfillerStatusForConcept(Concept concept) {
    if (concept.equals(labOrderConfig.getFulfillerStatusInProgressConcept())) return IN_PROGRESS;
    if (concept.equals(labOrderConfig.getFulfillerStatusCompletedConcept()))  return COMPLETED;
    if (concept.equals(labOrderConfig.getFulfillerStatusExceptionConcept()))  return EXCEPTION;
    if (concept.equals(labOrderConfig.getFulfillerStatusReceivedConcept()))   return RECEIVED;
    return null;
}
```

Note: the answer concept GPs are only needed when reading or writing status values. If a
site configures only the question concept GP (`pihapps.labs.fulfillerStatusConcept`) but
not the answer GPs, the encounter linkage still works — the linkage query filters by
`obs.order_id` and `obs.concept_id` only, never by value.

**Option B: Text-based "Lab Order Status" (new concept, text type)**

A new text-type concept whose `obs.valueText` stores the FulfillerStatus enum string
directly: `"IN_PROGRESS"`, `"COMPLETED"`, `"EXCEPTION"`, `"RECEIVED"`.

- No coded answer concepts or per-value GPs needed; enum string is self-documenting
- Trivially extensible if new statuses are added — no concept curation required
- The value is a system field, not a clinical observation, so the lack of coding is acceptable
- Requires creating one new concept (the question concept)
- Querying by status value uses `obs.value_text` (not indexed), but the linkage query never
  filters by value — only by `obs.order_id` and `obs.concept_id`, both indexed
- Status history is readable by comparing `obs.valueText` directly to the enum name

**Recommendation:** Option A with "Fulfillment Status" as the question concept requires the
least new concept creation (at most one new answer concept for RECEIVED/null) and is
semantically coherent with its existing use in the PIH system. The four per-value GPs add
configuration overhead but give each mapping explicit control.

Option B eliminates the coded answer concepts and per-value GPs entirely at the cost of one
new concept, and avoids any future drift if the FulfillerStatus enum is extended. It is a
valid alternative if the configurability of Option A is not needed.

The question concept UUID (either option) is also added to
`pihapps.labs.fulfillerEncounterLinkingConcepts` for pihemr deployments.

### Configurable Linking Concepts

A new global property `pihapps.labs.fulfillerEncounterLinkingConcepts` accepts a
comma-separated list of concept UUIDs. The fulfiller encounter lookup query becomes:

```sql
SELECT encounter_id FROM obs
WHERE order_id = ?
  AND voided = 0
  [AND concept_id IN (...)]   -- only if GP is set
ORDER BY obs_datetime DESC
LIMIT 1
```

- **GP empty / unset**: no concept filter — any obs with `obs.order_id` set counts.
  Maximum compatibility mode; covers all legacy data without migration.
- **GP set to one or more concept UUIDs**: only obs of those concepts are considered.
  Used to scope lookups on deployments where the data is well-defined.

The reverse direction (encounter → orders) uses the same concept filter:

```sql
SELECT DISTINCT order_id FROM obs
WHERE encounter_id = ?
  AND order_id IS NOT NULL
  AND voided = 0
  [AND concept_id IN (...)]
```

### Why obs.order_id Is the Right Index

`obs.order_id` has a dedicated database index (`KEY obs_order (order_id)`). A lookup by
`obs.order_id = ?` is a direct indexed FK scan — far cheaper than the current string match on
`value_text`. The entire N orders on a page can be resolved in one batched query using
`order_id IN (...)` rather than N separate queries.

---

## Compatibility by Deployment

### Rwanda (laboratorymanagement-v2 → pihapps migration)

Set `pihapps.labs.fulfillerEncounterLinkingConcepts` to **empty / unset**.

| Data | How found |
|---|---|
| Existing labs-v2 results | Any obs with `obs.order_id` set → result obs in the order's encounter |
| New pihapps encounters | Any obs with `obs.order_id` set → fulfiller status obs written at specimen collection |

No migration required. The "any obs" mode covers both data shapes immediately. The pre-results
window for new pihapps encounters is covered because the fulfiller status obs is written at
specimen collection time, before any result obs exist.

**Note on pre-results window for historical labs-v2 data:** labs-v2 has no specimen collection
step — obs.order_id is only set on result obs. Historical orders with no results yet will not
have a findable encounter until results are entered. This is consistent with the current
behavior, where labs-v2 never had a specimen collection encounter concept.

### pihemr (labworkflow OWA → pihapps migration)

Set `pihapps.labs.fulfillerEncounterLinkingConcepts` to include both:
1. The **testOrderNumber concept** UUID (covers existing labworkflow encounters)
2. The **fulfiller status concept** UUID (covers new pihapps encounters)

| Data | How found |
|---|---|
| Existing labworkflow results | testOrderNumber obs has `obs.order_id` set (confirmed) → found via testOrderNumber concept |
| New pihapps encounters | fulfiller status obs has `obs.order_id` set → found via fulfiller status concept |

No migration required. labworkflow data does not need to be backfilled with fulfiller status obs
because the testOrderNumber concept is in the GP list and those obs already have `obs.order_id`
set. New pihapps data does not need testOrderNumber obs written at all.

---

## Relationship to orders.fulfiller_status

OpenMRS core stores current fulfiller status on `orders.fulfiller_status`. The new fulfiller
status obs does not replace this field — it supplements it with history and with the encounter
linkage (which the core field cannot provide). The recommended approach is to keep both in sync:
when pihapps writes a fulfiller status obs, it also updates `orders.fulfiller_status` via the
existing `orderService.updateOrderFulfillerStatus()` call. All existing code that reads
`order.getFulfillerStatus()` continues to work unchanged.

---

## What Happens to testOrderNumberConcept

The `testOrderNumberConcept` GP and the testOrderNumber obs are no longer needed as the
linkage mechanism for new pihapps deployments. In practice:

- **Rwanda**: was never written by labs-v2; no action needed.
- **pihemr**: existing labworkflow encounters already have these obs and they remain valuable
  for backward-compatible lookup during transition. Once all active encounters are pihapps
  encounters, the concept can be removed from the GP list.
- **pihapps code**: the `specimenCollectionEncounter.gsp` no longer needs to write
  testOrderNumber obs. The `testOrderNumberConcept` GP can be deprecated once all active sites
  are migrated.

---

## Known Risks and Gotchas

### 1. labworkflow obs.order_id assumption

The PIHEMR compatibility path assumes that all existing labworkflow `testOrderNumberConcept`
obs have `obs.order_id` set. This was confirmed from code analysis of
`openmrs-react-components` (sagas.js line 98), but should be validated against real production
data before relying on it as the sole lookup mechanism. A small SQL query can verify:

```sql
SELECT COUNT(*) FROM obs o
JOIN concept c ON o.concept_id = c.concept_id
JOIN concept_name cn ON c.concept_id = cn.concept_id
WHERE cn.name = 'Test order number'   -- or filter by concept UUID
  AND o.voided = 0
  AND o.order_id IS NULL;
```

If this returns non-zero, those encounters would not be found under the concept-filtered GP
mode. Mitigation: either run a backfill to set `obs.order_id` on those rows, or include
`testOrderNumberConcept` in the GP list with the "any obs" fallback as well (i.e., also set GP
to empty for those sites temporarily).

### 2. Fulfiller status concept definition

The fulfiller status concept and its coded answers (RECEIVED, IN_PROGRESS, COMPLETED, EXCEPTION)
need to be defined and added to the relevant concept dictionaries (PIH, CIEL, or site-specific).
This is an upfront cost before the new mechanism can be used. Until the concept exists, pihapps
cannot write the new obs.

### 3. Pre-results window for historical Rwanda data

Existing labs-v2 orders with no results yet will not have a findable fulfiller encounter
(because no obs with `obs.order_id` exists yet for them). These orders will correctly appear as
AWAITING_FULFILLMENT in pihapps. This is accurate — they have no specimen collection encounter
— but may surprise users expecting to see in-flight orders from the legacy system. The
resolution is that these orders go through the pihapps specimen collection step normally.

### 4. Multiple obs per order in an encounter

An encounter fulfilling multiple orders will have one fulfiller status obs per order. The
reverse query (encounter → orders) returns distinct `order_id` values from the obs set — this
works correctly. On status transitions, new obs are added without voiding old ones (for
history), so the query correctly takes the most recent.
