# Lab Data Model Comparison

This document tracks the analysis of how three lab systems model their data, in support of
evolving `openmrs-module-pihapps` to replace both legacy systems.

See also: [`lab-fulfiller-encounter-linkage-design.md`](lab-fulfiller-encounter-linkage-design.md)
for the proposed redesign of how pihapps links orders to fulfiller encounters, and how that
design is backward-compatible with both legacy systems.

## Systems Under Comparison

| System | Repo | Used In |
|---|---|---|
| **laboratorymanagement-v2** | `rwanda-emr/openmrs-module-laboratorymanagement-v2` | Rwanda EMR |
| **labworkflow OWA** | `openmrs/openmrs-owa-labworkflow` + `openmrs/openmrs-react-components` | pihemr distro |
| **pihapps** | `pih/openmrs-module-pihapps` (this repo) | Replacing both |

---

## 1. Lab Orders

All three systems use OpenMRS `TestOrder` stored in the `orders` table. No custom tables.

| Detail | laboratorymanagement-v2 | labworkflow OWA | pihapps |
|---|---|---|---|
| Order type config | `laboratorymanagement.orderType.labOrderTypeId` | `labworkflowowa.testOrderType` | `pihapps.labs.labOrderType` (falls back to both legacy GPs) |
| Orderable concepts | `laboratorymanagement.labExamCategory` — hardcoded concept IDs | `orderentryowa.labOrderablesConceptSet` — concept set UUID | `orderentryowa.labOrderablesConceptSet` — same as labworkflow |
| Concept hierarchy | 3-level: category → group → individual test (grandfather/parent/child) | 2-level via concept set: category → test (panels as set members) | 2-level via concept set: category → test (panels as set members) |
| Fulfiller status | Never set by module; backfilled to COMPLETED by `MarkOrderAsFulfilledEventHandler` in `openmrs-module-rwandaemr` when obs are created | Set after results entry via `saveFulfillerStatus` saga: EXCEPTION if "did not perform" obs present, COMPLETED if result obs present, IN_PROGRESS if encounter exists with no result obs | Set to IN_PROGRESS on specimen collection, COMPLETED on results entry. Orders start with null fulfiller status; `OrderFulfillmentStatus.AWAITING_FULFILLMENT` treats both null and RECEIVED as the same state — pihapps never explicitly sets RECEIVED |
| Urgency | Not used | ROUTINE / STAT | ROUTINE / STAT |

---

## 2. Accession Numbers / Lab IDs

| Detail | laboratorymanagement-v2 | labworkflow OWA | pihapps |
|---|---|---|---|
| Stored on `orders.accession_number` | YES | YES (set via `PUT /order/{uuid}` at results entry time) | YES (set during specimen collection) |
| Stored as concept obs | NO | NO | YES — `labIdentifierConcept` obs (CIEL:162086) in the specimen collection encounter |
| Stored on `obs.accession_number` for result obs | YES — set on every result obs | NO | NO (**gap — see §6**) |
| When assigned | At specimen receipt, before results entry | At results entry time (same step) | During specimen collection (before results entry) |

---

## 3. Encounter Model

This is the most significant structural difference between the three systems.

### laboratorymanagement-v2

One encounter for everything. Orders and their result obs both live in the same encounter,
of the single configured type (`LAB_TEST_ENCOUNTER_TYPE` / `laboratorymanagement.encounterType.labEncounterTypeId`).
`getLabEncounter()` finds or creates an encounter of this type for the patient on the current
date, reusing it if one already exists. Result obs are written back to `labOrder.getEncounter()` —
i.e. the same encounter the order is in.

```
[LAB TEST encounter]
  └─ TestOrder  (accession_number = "RW-12345")
  └─ Obs: resultConcept = value  (obs.order_id set, obs.accession_number set)
  └─ Obs: resultConcept = value  (obs.order_id set, obs.accession_number set)
```

### labworkflow OWA

Single encounter containing both specimen metadata and results. The encounter is linked to
the order by a `testOrderNumberConcept` obs whose value is `order.orderNumber`. Result obs
also have `obs.order_id` set: `LabResultEntry.jsx` passes `orderForObs={selectedOrder}` to
`EncounterFormPanel`, and the react-components form saga sets `obs.order = orderUuid` for
each submitted obs (sagas.js line 98).

```
[Lab Results encounter]
  └─ Obs: testOrderNumberConcept    = "ORD-41554"     ← links encounter to order
  └─ Obs: specimenReceivedDate      = 2024-01-15
  └─ Obs: estimatedCollectionDate   = true/false
  └─ Obs: testLocation              = coded
  └─ Obs: resultsDate               = 2024-01-16
  └─ Obs: resultConcept             = value            (obs.order_id set)
  └─ Obs: resultConcept             = value            (obs.order_id set)
```

### pihapps

One Laboratory Encounter holds both specimen metadata and results — the same structure as
labworkflow. The difference is that this encounter is created in two phases: first at specimen
reception (specimen metadata obs are recorded and fulfillerStatus set to IN_PROGRESS), then
results are added to that same encounter later (fulfillerStatus set to COMPLETED). Orders are
placed in a separate LAB TEST encounter by a different tool; the Laboratory Encounter is
pihapps' concern. Result obs have `obs.order_id` set (via the standard OpenMRS
encounter/obs REST endpoint).

Note: unlike labworkflow and pihapps, laboratorymanagement-v2 uses the **same** encounter
for both orders and results — there is no separate ordering encounter in that system.

```
[LAB TEST encounter]          [Laboratory Encounter] (specimen collection + results)
  └─ TestOrder           →      └─ Obs: testOrderNumberConcept  = "ORD-41554"
       fulfillerStatus:           └─ Obs: labIdentifierConcept   = "RW-12345"
         IN_PROGRESS →            └─ Obs: specimenReceivedDate   = 2024-01-15
         COMPLETED                └─ Obs: testLocation           = coded
       accessionNumber:           └─ Obs: resultsDate            = 2024-01-16
         "RW-12345"               └─ Obs: resultConcept          = value  (obs.order_id set)
                                  └─ Obs: resultConcept          = value  (obs.order_id set)
```

---

## 4. How Results Are Linked to Orders

This is the most important query-time concern.

| Link mechanism | laboratorymanagement-v2 | labworkflow OWA | pihapps |
|---|---|---|---|
| `obs.order_id` set on result obs | YES | YES (`orderForObs` prop → sagas.js line 98) | YES (via encounter POST) |
| `testOrderNumberConcept` obs in encounter | NO | YES | YES |
| `obs.order_id` set on `testOrderNumberConcept` obs | N/A | **YES** — form saga applies `obs.order = orderUuid` to all obs including testOrderNumber (sagas.js line 98) | **NO** — built manually in `specimenCollectionEncounter.gsp` with no `order` field |
| `obs.accession_number` set on result obs | YES | NO | NO |

All three systems set `obs.order_id` on result obs. labworkflow additionally sets `obs.order_id`
on the testOrderNumber obs (because it goes through the same form saga as result obs).
pihapps writes the testOrderNumber obs manually without `obs.order_id`, so the only indexed
FK linkage in pihapps encounters is on result obs — not on the testOrderNumber obs used for
encounter lookup. laboratorymanagement-v2 has no testOrderNumber obs and relies solely on
`obs.order_id` on result obs (and `obs.accession_number`) for all linkage.

---

## 5. Specimen Metadata Concepts

| Concept | laboratorymanagement-v2 | labworkflow OWA | pihapps GP |
|---|---|---|---|
| Specimen received date | Not recorded as obs | Hardcoded: `6234d61b-4c77-4af6-9bbb-533e44c03f24` | `pihapps.labs.specimenReceivedDateConcept` (default PIH:21057) |
| Specimen/lab identifier | Not an obs (only on order) | Not an obs (only on order) | `pihapps.labs.labIdentifierConcept` (default CIEL:162086) |
| Test location | Not recorded | `labworkflowowa.locationOfLaboratory` | `pihapps.labs.locationOfLaboratory` |
| Estimated collection date | Not recorded | `labworkflowowa.estimatedCollectionDateQuestion/Answer` | `pihapps.labs.estimatedCollectionDateQuestion/Answer` |
| Results date | Not recorded | `labworkflowowa.labResultsDateConcept` | `pihapps.labs.resultsDateConcept` |
| Did not perform | Not recorded | `labworkflowowa.didNotPerformQuestion/Answer/ReasonQuestion` | `pihapps.labs.didNotPerformReason` |
| Test order number | Not recorded (not used) | `labworkflowowa.testOrderNumberConcept` | `pihapps.labs.testOrderNumberConcept` |

---

## 6. Compatibility Gaps and Open Questions

### Gap A — `obs.accession_number` not written by pihapps

**Status:** Known gap, not yet fixed.

laboratorymanagement-v2 sets `obs.accession_number` on every result obs. Some legacy query
paths (e.g. `LaboratoryServiceImpl.getObsByLabCode()`) filter by this field and will not
find results entered via pihapps. labworkflow never set it either, so this is not a regression
for pihemr sites — but it does affect Rwanda.

Fix: in `PihAppsServiceImpl.saveEncounterFulfillingOrders()`, after extracting the
accessionNumber, iterate result obs in the encounter and call `obs.setAccessionNumber()`.
Small change, low risk.

### Gap B — Results entry requires a specimen collection encounter

**Status:** Known gap, architectural.

The `recordLabResults` fragment hard-aborts if `order.fulfillerEncounter` is null. Neither
legacy system required a separate specimen collection step before entering results. This
blocks pihapps results entry for any order that went through the legacy workflow without a
pihapps specimen collection encounter.

Options:
1. Relax the guard — allow results entry without a pre-existing specimen encounter, creating
   one implicitly or treating it as optional.
2. Require users to complete a specimen collection step in pihapps for in-flight orders
   before entering results (workflow change).
3. Configurable single-encounter mode (see Gap D).

### Gap C — Legacy fulfiller encounter lookup

**Status:** Open — addressed by redesign in `docs/lab-fulfiller-encounter-linkage-design.md`.

`getFulfillerEncounterForOrder()` only finds pihapps-style specimen encounters (via
testOrderNumberConcept obs). Legacy orders with results have no such obs, causing them to
appear as "orphaned orders" (has fulfillerStatus but no fulfillerEncounter) in the pihapps UI.

The redesign addresses this with a configurable GP
`pihapps.labs.fulfillerEncounterLinkingConcepts` (comma-separated concept UUIDs):
- Empty/unset = any obs with `obs.order_id` counts (covers all legacy data without migration).
- Set to specific concepts = only those concepts, e.g. testOrderNumber + fulfillerStatus.
- The testOrderNumber obs is replaced by a **fulfiller status obs** (with `obs.order_id` set)
  as pihapps' native linkage mechanism going forward.

See the design document for full deployment details per site.

### Gap D — Required two-phase encounter workflow vs. optional single-phase

**Status:** Open design question.

All three systems use one Laboratory Encounter for lab work (specimen + results). The
difference is that pihapps requires this encounter to be created in two separate steps:
first specimen collection (which creates the encounter), then results entry (which adds obs
to the existing encounter). Neither legacy system required this separation — labworkflow
created the encounter in a single results-entry step; laboratorymanagement-v2 did not
require any prior encounter creation at all.

pihapps' two-phase workflow is a deliberate improvement (it enables specimen reception
tracking independent of results), but it creates friction for:
- Migration from either legacy system (no pre-existing specimen encounters)
- Sites that don't want/need a distinct specimen reception step

A configurable single-phase mode — where specimen metadata and results can be entered
together in one step, matching the labworkflow model operationally — would eliminate the
specimen-collection prerequisite and make historical data from either legacy system look
identical to new pihapps data.

---

## 7. Global Property Lineage

pihapps properties were designed to mirror labworkflow's with a namespace change. Labs-v2 is
largely orthogonal (separate namespace, some hardcoded values).

The following mappings are **actual code fallback chains** in `LabOrderConfig` — if the
pihapps GP is not set, the labworkflow GP is read automatically:

```
labworkflow GP                              → pihapps GP
─────────────────────────────────────────────────────────────────────────────
labworkflowowa.testOrderType                → pihapps.labs.labOrderType
labworkflowowa.testOrderNumberConcept       → pihapps.labs.testOrderNumberConcept
labworkflowowa.labResultsEntryEncounterType → pihapps.labs.specimenCollectionEncounterType
labworkflowowa.locationOfLaboratory         → pihapps.labs.locationOfLaboratory
labworkflowowa.estimatedCollectionDateQuestion → pihapps.labs.estimatedCollectionDateQuestion
labworkflowowa.estimatedCollectionDateAnswer   → pihapps.labs.estimatedCollectionDateAnswer
labworkflowowa.didNotPerformReason          → pihapps.labs.didNotPerformReason
labworkflowowa.labCategoriesConceptSet      → pihapps.labs.labResultCategoriesConceptSet
orderentryowa.labOrderablesConceptSet       → orderentryowa.labOrderablesConceptSet (same, no alias)
```

The following pihapps GPs have **no labworkflow fallback** — they use hardcoded defaults:

```
pihapps.labs.specimenReceivedDateConcept    default: PIH:21057   (labworkflow hardcoded 6234d61b-...)
pihapps.labs.resultsDateConcept             default: PIH:10783   (labworkflow GP: labworkflowowa.labResultsDateConcept — no fallback)
pihapps.labs.labIdentifierConcept           default: CIEL:162086 (no labworkflow equivalent)
```

The following labs-v2 GPs are **actual code fallbacks** in `LabOrderConfig`:

```
laboratorymanagement.orderType.labOrderTypeId  → pihapps.labs.labOrderType (fallback chain)
laboratorymanagement.multipleAnswerConceptIds  → pihapps.labs.multipleAnswerConcepts (fallback)
```

---

## 8. Rwanda-Specific Context

- `openmrs-module-rwandaemr` contains `MarkOrderAsFulfilledEventHandler`, which fires on
  Obs CREATE and sets `fulfillerStatus = COMPLETED` on any TestOrder where it is currently
  null. A companion scheduled task (`MarkOrdersAsFulfilledOrExpiredTask`) batch-processes
  any orders the real-time handler missed. This addresses the Rwanda-specific gap where
  laboratorymanagement-v2 never set fulfiller status.

- `openmrs-distro-rwandaemr` configures all pihapps lab GPs. The new
  `pihapps.labs.fulfillerEncounterLinkingConcepts` GP will be left empty (unset) for Rwanda,
  enabling the "any obs with obs.order_id" compatibility mode for legacy data.

- Rwanda lab categories (Hematology, Parasitology, Urinary Chemistry, Bacteriology,
  Hemostasis, Immunoserology, Tumour Markers, Blood Chemistry, Fertility Hormones,
  Toxicology, Thyroid Function) are the same concept set in both the legacy module config
  and the pihapps distro config.
