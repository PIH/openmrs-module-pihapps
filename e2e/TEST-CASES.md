# Test cases: lab results entry, validation, Lab ID page, culture plug-in

Test cases written for RWA-1089 (PIH/openmrs-module-pihapps#66, PIH/openmrs-module-rwandaemr#42,
PIH/openmrs-distro-rwandaemr#42). The specs in `specs/` implement them; each test is named after its case id.
They also serve as manual test cases.

Each case gives: **Id**, **Sites** (SL = a pihemr-based site, Sierra Leone, also standing in for Haiti; RW = Rwanda
with rwandaemr and the distro), **Validation** (off, on, or either: the global property
`pihapps.labs.requireResultValidation`, changed under Settings), **Users**, **Preconditions**, **Steps**, **Expected**.

## Conventions

- **Lab tech**: has Add Encounters and Edit Orders (enters results). **Validator**: also has
  `Task: pihapps.labs.validateResults` and Edit Orders. **Clinician**: views results only.
  The users the specs use for each role are set per site in `lib/instances.ts`.
- **Collected order**: an order whose specimen was collected (specimen collection encounter with a Lab ID). Orders
  collected together share a Lab ID.
- **Lab ID page**: `labResultsByLabId.page`, opened from the lab order list ("Enter results by Lab ID" patient action,
  or the validate action). **Single order form**: the results form on the lab order list (results icon on an order).
- "Preliminary/final/amended" are obs statuses; "awaiting validation", "returned", "validated" are the order's
  validation state shown on the lab order list.
- When a step says "check the database", the same can be seen through the order's validation history on the
  validate page; database checks are given for automation.

---

## A. Results entry, validation off (regression for existing sites)

**A1 – Single order form saves final results** · SL, RW · off · Lab tech
- Pre: a collected numeric test (e.g. HGB).
- Steps: open the order's results icon; choose Completed; enter 13; Save.
- Expected: the form closes; the order shows Completed; the patient lab results page shows 13 (final); no
  validation event exists for the order; labels say "Completed", never "Submit for validation".

**A2 – Selected status clicked again keeps the value** · SL, RW · either · Lab tech
- Pre: single order form open on a collected order, Completed chosen, a value entered.
- Steps: click Completed again.
- Expected: the value stays; nothing is cleared. (Fix: clicking the selected status used to clear the field.)

**A3 – Abnormal, critical and invalid values** · SL, RW · either · Lab tech
- Pre: a numeric test with a normal range, a critical limit and an absolute minimum of 0 (SL HGB: 11.5–17.8,
  critical < 7, absolute ≥ 0).
- Steps: on both the single order form and the Lab ID page, enter 25, then 5, then −1.
- Expected: 25: orange border on the field, "⚠ Abnormal value" beside the normal range; 5: red border, "Critical
  value" beside the range; −1: red border, "Minimum allowed value: 0" under the field, and Save refuses with "errors
  with one or more fields". "Add comment" and the units stay in place in every case (no shifting of the row).

**A4 – Result fields stay aligned** · SL, RW · either · Lab tech
- Steps: on the Lab ID page with two tests, flag one abnormal and leave the other normal.
- Expected: units and "Add comment" line up on both rows; the status buttons stay on one line.

**A5 – Re-saving keeps results in their panel group** · SL, RW · off · Lab tech
- Pre: a panel order with results saved.
- Steps: edit one member's value and save.
- Expected: the edited result stays in the same panel obs group; a new member result of another order is not
  moved into this order's group.

**A6 – Mark all appears only with several tests** · SL, RW · either · Lab tech
- Steps: open the Lab ID page for a Lab ID with two tests in one category, then for a Lab ID with one test.
- Expected: "Mark all: Completed" (or "Submit for validation" with validation on) appears for the first, not the
  second.

**A7 – Error message from the server is shown as text** · SL · either · Lab tech
- Steps: make a save fail (e.g. stop the server mid-save, or a result invalid on the server).
- Expected: the error shows as plain text, never "[object Object]" and never rendered HTML.

## B. Lab ID page

**B1 – One Lab ID at a time, with a choice row** · SL, RW · either · Lab tech
- Pre: a patient with two collected Lab IDs not completed (e.g. LAB-A10 with HGB+HCT, LAB-B20 with HGB).
- Steps: open "Enter results by Lab ID" from the patient row.
- Expected: a row of choices lists both Lab IDs (date and tests), the first (STAT first, then oldest) is open; clicking
  the other switches to it (`labId` in the URL); with a single Lab ID no choice row is shown.

**B2 – Orders of two order encounters sharing a Lab ID show together** · SL, RW · either · Lab tech
- Pre: two orders placed in different order encounters, collected together with one Lab ID.
- Expected: both tests appear in that Lab ID's section.

**B3 – Only changed tests are saved** · SL, RW · either · Lab tech
- Pre: a Lab ID with two collected tests, both untouched.
- Steps: enter a value for one test only, set it Completed (Submit for validation), Save.
- Expected: only that order changes; the other keeps its status, gets no event and no result date. Saving again with
  no changes says "There are no changes to save."

**B4 – Orders on hold are left on hold** · RW, SL · either · Lab tech
- Pre: a collected order put on hold, and another order with the same Lab ID.
- Steps: enter and save the other order.
- Expected: the on-hold order is still on hold.

**B5 – A failed save of one specimen is not repeated** · SL, RW · either · Lab tech
- Pre: a Lab ID recorded for two specimen collections (rare), both with entries.
- Steps: save while the second specimen's request fails (automation: route the second POST to a 500).
- Expected: the first specimen is greyed out as saved; the error says "Already saved: Lab ID …"; saving again only
  sends the second; each order has exactly one result.

**B6 – More than 100 orders** · either · Lab tech
- Expected: a note says only the first 100 orders are shown.

## C. Validation on: submitting

**C1 – Submit for validation** · SL, RW · on · Lab tech
- Steps: on the Lab ID page or single order form, enter results, choose "Submit for validation", Save.
- Expected: the order stays In progress and shows "Awaiting validation" on the lab order list; results are
  preliminary; a RESULTS_SUBMITTED event is recorded by the lab tech; the entry page's hint says results are not
  shown on the lab results pages until validated.

**C2 – Clinicians do not see results awaiting validation** · SL, RW · on · Clinician
- Expected: the patient lab results page and the trends chart do not show the submitted results.

**C3 – Correcting a result awaiting validation** · SL, RW · on · Lab tech
- Steps: reopen the order (it opens as Submit for validation, without In progress), change the value, Save.
- Expected: still awaiting validation; the new value is preliminary; no second RESULTS_SUBMITTED.

## D. Validation on: validating (the per-test decision page)

**D1 – Validate all** · SL, RW · on · Validator
- Pre: a Lab ID with two tests awaiting validation, entered by the lab tech.
- Steps: open the validate action (patient row, or the order's icon, which opens that order's Lab ID); leave each test
  on Validate; the summary reads "To validate: 2 · To return: 0"; Save.
- Expected: "Validated: 2 · Returned for correction: 0"; the orders are Completed; results final; RESULTS_VALIDATED by
  the validator; clinicians now see the results.

**D2 – Validate one, return the other with its own reason** · SL, RW · on · Validator
- Steps: set the second test to Return; Save without a reason; then type a reason and Save.
- Expected: without a reason: "Reason is required" under the test and at the top, nothing saved; the top message
  clears once a reason is typed. After Save: the first test validated; the second stays In progress, returned
  (RESULTS_RETURNED with the reason), results still preliminary.

**D3 – Returned results go back to the lab** · SL, RW · on · Lab tech
- Expected: the returned test shows "Returned for correction: <reason>" on the entry page; re-submitting puts it
  back awaiting validation.

**D4 – Abnormal flags on the validate page** · SL, RW · on · Validator
- Expected: abnormal/critical results are coloured and flagged beside the normal range, as on entry.

**D5 – Partial failure of a save** · SL, RW · on · Validator
- Steps: validate one test and return another while the return request fails (automation: route it to a 500).
- Expected: the validated test is greyed out with "Validated"; the error says "Already recorded: <test>"; saving
  again only sends the return.

**D6 – Mark all: Validate** · either · on · Validator
- Expected: shown only for a category with more than one test the user can decide on; sets them all to Validate.

## E. Separation of duties and state changes

**E1 – Whoever entered results cannot validate them** · SL, RW · on · Lab tech with the validate privilege
- Expected: the test shows "You entered or changed these results, so another user must validate them" instead of the
  decision; a direct request is refused with the same message (translated).

**E2 – Whoever submitted results cannot validate them** · RW, SL · on · Validator who submits
- Pre: results entered by another user (or a lab machine) and left In progress.
- Steps: the validator re-saves them as Submit for validation without changing them, then opens the validate page.
- Expected: the validator cannot validate them.

**E3 – Editing the specimen collection does not block validating** · on · Validator
- Steps: the validator edits the order's specimen collection (e.g. received date), then validates.
- Expected: allowed.

**E4 – Not performed withdraws the submission** · SL, RW · on · Lab tech
- Steps: submit results; mark the order Not performed; later set it back to In progress (single order form).
- Expected: after Not performed, a RESULTS_WITHDRAWN event; back In progress, the order is not awaiting validation
  and cannot be validated until submitted again. Same after "revert to ordered".

**E5 – Results changed while the validator has the page open** · SL, RW · on · Lab tech, Validator
- Steps: the validator opens the validate page; the lab tech corrects a value and saves; the validator clicks Save
  with the test on Validate.
- Expected: refused: "The results of order ORD-… have changed since you opened them. Reload the page to review them
  again." Nothing recorded; after reloading, validating works.

**E6 – Editing a validated result records an amendment** · SL, RW · on · Lab tech
- Steps: open a validated order on the single order form, change a value, Save.
- Expected: the order stays Completed; the new value is amended (previous version = the old one); RESULTS_AMENDED is in
  the validation history; clinicians see the amended value. Saving again without changes records nothing.

**E7 – Validation events need the validate privilege** · on · Lab tech without it
- Expected: the lab order list shows no validate action; a direct REST request to validate or return gets 403.

## F. Culture / antimicrobial susceptibility plug-in (Rwanda)

Precondition for F: the bacteriology concepts loaded (distro `rwandaemr-content/.../concepts/bacteriology.csv`); a
`Bact_Urines` order collected. The exams are only orderable (and only shown under Bacteriology) once added to
`laboratorymanagement.currentLabRequestFormConceptIDs` on the site.

**F1 – Exam layout** · RW · either · Lab tech
- Expected: Urines shows the wet mount and Gram stain panels as sections of text results, then Culture, then
  Antimicrobial susceptibility (hidden).

**F2 – Susceptibility appears once an organism is isolated** · RW · either · Lab tech
- Steps: set Culture to "Organism isolated".
- Expected: the susceptibility row appears with an Enter button.

**F3 – Enter susceptibility** · RW · either · Lab tech
- Steps: Enter; choose Escherichia coli for germ 1; mark two antibiotics S and R; Done; Save (Submit for validation
  with validation on).
- Expected: summary "Escherichia coli: 1 S · 0 I · 1 R"; saved in one request with the order's other results; with
  validation on the whole group (isolate, organism, antibiotic results) is preliminary and awaits validation; with
  validation off it is final and the order completed.

**F4 – Dialog rules** · RW · either · Lab tech
- Expected: the same organism twice is refused ("The same organism is chosen for two germs"); clicking a chosen
  S/I/R again clears it; search and "Only antibiotics with a result" filter the list; Cancel discards; labels are
  translated (French locale).

**F5 – Validate a culture** · RW · on · Validator
- Expected: the validate page shows the susceptibility as a table (antibiotics × organisms, S/I/R coloured, legend);
  validating makes the whole group final.

**F6 – Clinician view** · RW · either · Clinician
- Expected: the patient lab results page shows the culture result and the susceptibility table.

**F7 – Edit a validated culture** · RW · on · Lab tech
- Steps: open the validated order on the single order form; Enter; change one antibiotic from R to S; Done; Save.
- Expected: the old group voided, a new group amended with the old one as previous version; RESULTS_AMENDED.
- Variant: Enter, Done without changing anything, Save: nothing changes, no amendment.

**F8 – Culture contradicts the susceptibility** · RW · either · Lab tech
- Steps: with a susceptibility entered, change Culture to "No organism isolated".
- Expected: the row stays, with a warning that the culture result does not say an organism was isolated.

**F9 – Answers no longer configured are kept** · RW · either · Lab tech
- Pre: a culture whose organism or antibiotic is no longer an answer of its question.
- Expected: the dialog still shows it (named as recorded); editing other values keeps it.

**F10 – Printing** · RW (Butaro and rwandaemr-content sites) · either · Lab tech
- Steps: print the patient's results from the lab order list.
- Expected: completed orders print, including the susceptibility table of cultures; with none completed: "There are no
  completed results to print".

## G. Rwanda site configuration

**G1 – Lab order list actions** · RW (each site including rwandaemr-content: Butaro, Kirehe, Rwinkwavu, RwandaMOH)
- Expected: "Enter results by Lab ID" only on rows with collected orders; payment badges (Paid / Partially paid /
  Not paid / Not billed); Print; built-in validate action only with validation on and the privilege.

**G2 – Results SMS waits for validation** · RW · on/off
- Expected: with validation off, the SMS follows results as before; with validation on, only once validated.

**G3 – Orders completed outside pihapps** · RW · on
- Pre: results linked to a lab order by another path (form or integration) with no fulfiller status.
- Expected: with validation on, the order is left In progress (lab submits it); with validation off, completed as
  before.

## H. Upgrade: migration of the earlier Rwanda validation (rwandaemr changeset)

Not automated: the migration runs once at startup against a prepared database.

**H1 – No earlier table** · RW · either
- Expected: startup proceeds; the migration and drop changesets are marked ran.

**H2 – Earlier records, validation on** · RW · on
- Pre: `rwandaemr_lab_result_validation` with a VALIDATED and a RETURNED row; an order "Results awaiting validation".
- Expected: RESULTS_VALIDATED and RESULTS_RETURNED (with the reason) by the original user and time; the awaiting order
  gets RESULTS_SUBMITTED credited to who entered its results, and awaits validation; results of awaiting and
  returned orders preliminary (specimen obs stay final); table dropped; startup never halts.

**H3 – Earlier records, validation off** · RW · off
- Expected: the VALIDATED/RETURNED history is migrated; no RESULTS_SUBMITTED; no result changed; table dropped.

## I. Stale pages (one user after the other)

Requests made at the same instant are not guarded against (locking removed); these cases are the realistic ones.

**I1 – Two validators, same order** · on · both open the validate page, one validates, then the other
- Expected: the second gets "not awaiting validation"; one RESULTS_VALIDATED.

**I2 – Validate after another validator returned** · on
- Expected: refused, "not awaiting validation"; one decision recorded.

**I3 – Lab edits after the validator opened the page** · on · validator opens, lab saves a change, validator validates
- Expected: refused, "results have changed … reload". After reloading, validation succeeds.

## J. Validation off: nothing of validation shows (regression)

**J1** · SL, RW · off · Validator
- Expected: no validate action, no "Awaiting validation" filter option, no badges, labels "Completed", the validate
  page says no results await validation, and the patient lab results page shows all results as before.
