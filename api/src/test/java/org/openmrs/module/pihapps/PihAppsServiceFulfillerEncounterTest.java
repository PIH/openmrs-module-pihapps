package org.openmrs.module.pihapps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PihAppsServiceFulfillerEncounterTest extends BaseModuleContextSensitiveTest {

    @Autowired
    @Qualifier("pihappsService")
    private PihAppsService pihAppsService;

    private Concept statusConcept;
    private Concept inProgressConcept;
    private Concept completedConcept;
    private Concept exceptionConcept;
    private Concept receivedConcept;
    private Concept labIdConcept;
    private Concept otherConcept;

    @BeforeEach
    public void setup() throws Exception {
        executeDataSet("pihapps_lab_order_test_data.xml");

        statusConcept    = saveConceptCoded("Test Status");
        inProgressConcept = saveConcept("In Progress");
        completedConcept  = saveConcept("Completed");
        exceptionConcept  = saveConcept("Not Done");
        receivedConcept   = saveConcept("Pending");
        labIdConcept      = saveConcept("Lab Identifier");
        otherConcept      = saveConceptCoded("Other Concept");

        configureGps("");
    }

    // ==================== getFulfillerEncounterForOrder ====================

    @Test
    public void getFulfillerEncounterForOrder_shouldReturnEncounterFromObsWithOrderId() {
        Order order = getOrder(7);
        Encounter fulfillerEncounter = createEncounterForPatient(order.getPatient());
        saveLinkingObs(order, fulfillerEncounter, statusConcept, new Date());

        assertThat(pihAppsService.getFulfillerEncounterForOrder(order), equalTo(fulfillerEncounter));
    }

    @Test
    public void getFulfillerEncounterForOrder_shouldReturnNullWhenNoObsWithOrderId() {
        Order order = getOrder(7);
        assertThat(pihAppsService.getFulfillerEncounterForOrder(order), nullValue());
    }

    @Test
    public void getFulfillerEncounterForOrder_shouldIgnoreObsWithNullEncounter() {
        Order order = getOrder(7);
        Encounter fulfillerEncounter = createEncounterForPatient(order.getPatient());

        // older obs — has a real encounter
        saveLinkingObs(order, fulfillerEncounter, statusConcept, new Date(System.currentTimeMillis() - 10_000));

        // newer obs — no encounter (e.g. a did-not-perform reason obs written without an encounter)
        Obs noEncounterObs = new Obs();
        noEncounterObs.setPerson(order.getPatient());
        noEncounterObs.setConcept(statusConcept);
        noEncounterObs.setObsDatetime(new Date());
        noEncounterObs.setLocation(getLocation());
        noEncounterObs.setOrder(order);
        noEncounterObs.setValueCoded(inProgressConcept);
        Context.getObsService().saveObs(noEncounterObs, "");

        // should return the encounter from the older obs, not null from the newer one
        assertThat(pihAppsService.getFulfillerEncounterForOrder(order), equalTo(fulfillerEncounter));
    }

    @Test
    public void getFulfillerEncounterForOrder_shouldFilterByConceptWhenLinkingConceptsConfigured() {
        Order order = getOrder(7);
        Encounter fulfillerEncounter = createEncounterForPatient(order.getPatient());
        saveLinkingObs(order, fulfillerEncounter, statusConcept, new Date());

        // concept filter matches the obs concept → finds encounter
        configureGps(statusConcept.getUuid());
        assertThat(pihAppsService.getFulfillerEncounterForOrder(order), equalTo(fulfillerEncounter));

        // concept filter does NOT match the obs concept → finds nothing
        configureGps(otherConcept.getUuid());
        assertThat(pihAppsService.getFulfillerEncounterForOrder(order), nullValue());
    }

    // ==================== getEncounterFulfillingOrders ====================

    @Test
    public void getEncounterFulfillingOrders_shouldReturnOrdersLinkedViaObsOrderId() {
        Order order = getOrder(7);
        Encounter fulfillerEncounter = createEncounterForPatient(order.getPatient());
        saveLinkingObs(order, fulfillerEncounter, statusConcept, new Date());

        EncounterFulfillingOrders result = pihAppsService.getEncounterFulfillingOrders(fulfillerEncounter.getUuid());

        assertThat(result, notNullValue());
        assertThat(result.getOrders(), hasItem(order));
    }

    @Test
    public void getEncounterFulfillingOrders_shouldFilterByConceptWhenLinkingConceptsConfigured() {
        Order order = getOrder(7);
        Encounter fulfillerEncounter = createEncounterForPatient(order.getPatient());
        saveLinkingObs(order, fulfillerEncounter, statusConcept, new Date());

        // concept filter matches → finds order
        configureGps(statusConcept.getUuid());
        assertThat(pihAppsService.getEncounterFulfillingOrders(fulfillerEncounter.getUuid()).getOrders(), hasItem(order));

        // concept filter does NOT match → finds no orders
        configureGps(otherConcept.getUuid());
        assertThat(pihAppsService.getEncounterFulfillingOrders(fulfillerEncounter.getUuid()).getOrders(), empty());
    }

    // ==================== saveEncounterFulfillingOrders ====================

    @Test
    public void saveEncounterFulfillingOrders_shouldCreateFulfillerStatusObs() {
        Order order = getOrder(7); // fulfiller_status=RECEIVED → coerced to IN_PROGRESS
        Encounter encounter = createEncounterForPatient(order.getPatient());

        EncounterFulfillingOrders efo = new EncounterFulfillingOrders();
        efo.setEncounter(encounter);
        efo.setOrders(Collections.singletonList(order));
        pihAppsService.saveEncounterFulfillingOrders(efo);

        Obs statusObs = findStatusObs(efo.getEncounter(), order);
        assertThat("fulfiller status obs should be created", statusObs, notNullValue());
        assertThat(statusObs.getValueCoded(), equalTo(inProgressConcept));
        assertThat(statusObs.getOrder(), equalTo(order));
    }

    @Test
    public void saveEncounterFulfillingOrders_shouldNotCreateDuplicateObsWhenStatusUnchanged() {
        Order order = getOrder(102); // fulfiller_status=IN_PROGRESS
        Encounter encounter = createEncounterForPatient(order.getPatient());

        EncounterFulfillingOrders efo = new EncounterFulfillingOrders();
        efo.setEncounter(encounter);
        efo.setOrders(Collections.singletonList(order));

        pihAppsService.saveEncounterFulfillingOrders(efo);
        pihAppsService.saveEncounterFulfillingOrders(efo); // same status — should be a no-op

        long count = countStatusObs(efo.getEncounter(), order);
        assertThat("should not create duplicate obs for unchanged status", count, equalTo(1L));
    }

    @Test
    public void saveEncounterFulfillingOrders_shouldCreateNewObsWhenStatusChanges() {
        Order order = getOrder(102); // fulfiller_status=IN_PROGRESS
        Encounter encounter = createEncounterForPatient(order.getPatient());

        EncounterFulfillingOrders efo = new EncounterFulfillingOrders();
        efo.setEncounter(encounter);
        efo.setOrders(Collections.singletonList(order));

        pihAppsService.saveEncounterFulfillingOrders(efo); // creates IN_PROGRESS obs

        order.setFulfillerStatus(Order.FulfillerStatus.COMPLETED);
        pihAppsService.saveEncounterFulfillingOrders(efo); // should create a second COMPLETED obs

        assertThat("should create new obs for each status transition", countStatusObs(efo.getEncounter(), order), equalTo(2L));

        // most recent obs should reflect the new status
        Obs latest = findStatusObs(efo.getEncounter(), order);
        assertThat(latest.getValueCoded(), equalTo(completedConcept));
    }

    @Test
    public void saveEncounterFulfillingOrders_shouldThrowWhenOrderBelongsToDifferentPatient() {
        Order order = getOrder(101); // patient 7
        Encounter encounter = createEncounterForPatient(Context.getPatientService().getPatient(2)); // patient 2

        EncounterFulfillingOrders efo = new EncounterFulfillingOrders();
        efo.setEncounter(encounter);
        efo.setOrders(Collections.singletonList(order));

        assertThrows(IllegalArgumentException.class, () -> pihAppsService.saveEncounterFulfillingOrders(efo));
    }

    @Test
    public void saveEncounterFulfillingOrders_shouldThrowWhenFulfillerStatusConceptNotConfigured() {
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.fulfillerStatusConcept", "");
        Order order = getOrder(102);
        Encounter encounter = createEncounterForPatient(order.getPatient());

        EncounterFulfillingOrders efo = new EncounterFulfillingOrders();
        efo.setEncounter(encounter);
        efo.setOrders(Collections.singletonList(order));

        assertThrows(IllegalStateException.class, () -> pihAppsService.saveEncounterFulfillingOrders(efo));
    }

    // ==================== markOrdersAsNotFulfilled ====================

    @Test
    public void markOrdersAsNotFulfilled_shouldCreateExceptionStatusObsOnFulfillerEncounter() {
        Order order = getOrder(7);
        Encounter fulfillerEncounter = createEncounterForPatient(order.getPatient());
        saveLinkingObs(order, fulfillerEncounter, statusConcept, new Date());

        pihAppsService.markOrdersAsNotFulfilled(Collections.singletonList(order), null);

        // verify the exception status obs was written
        List<Obs> obs = Context.getObsService().getObservationsByPerson(order.getPatient());
        boolean hasExceptionObs = false;
        for (Obs o : obs) {
            if (statusConcept.equals(o.getConcept()) && order.equals(o.getOrder())
                    && exceptionConcept.equals(o.getValueCoded()) && !o.getVoided()) {
                hasExceptionObs = true;
                break;
            }
        }
        assertThat("exception status obs should be created on fulfiller encounter", hasExceptionObs, equalTo(true));
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.EXCEPTION));
    }

    @Test
    public void markOrdersAsNotFulfilled_shouldFindFulfillerEncounterWhenExistingObsHasNullEncounter() {
        Order order = getOrder(7);
        Encounter fulfillerEncounter = createEncounterForPatient(order.getPatient());
        saveLinkingObs(order, fulfillerEncounter, statusConcept, new Date(System.currentTimeMillis() - 10_000));

        // existing reason obs with no encounter — simulates legacy data
        Obs legacyReason = new Obs();
        legacyReason.setPerson(order.getPatient());
        legacyReason.setConcept(otherConcept);
        legacyReason.setObsDatetime(new Date());
        legacyReason.setLocation(getLocation());
        legacyReason.setOrder(order);
        legacyReason.setValueCoded(inProgressConcept);
        // no encounter set
        Context.getObsService().saveObs(legacyReason, "");

        // should still find the fulfiller encounter via getFulfillerEncounterForOrder fallback
        pihAppsService.markOrdersAsNotFulfilled(Collections.singletonList(order), null);

        List<Obs> obs = Context.getObsService().getObservationsByPerson(order.getPatient());
        boolean hasExceptionObs = false;
        for (Obs o : obs) {
            if (statusConcept.equals(o.getConcept()) && order.equals(o.getOrder())
                    && exceptionConcept.equals(o.getValueCoded()) && !o.getVoided()) {
                hasExceptionObs = true;
                break;
            }
        }
        assertThat("exception status obs should be written even when existing reason obs has no encounter", hasExceptionObs, equalTo(true));
    }

    // ==================== helpers ====================

    private void configureGps(String linkingConceptsValue) {
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.fulfillerStatusConcept", statusConcept.getUuid());
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.fulfillerStatusConcept.inProgress", inProgressConcept.getUuid());
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.fulfillerStatusConcept.completed", completedConcept.getUuid());
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.fulfillerStatusConcept.exception", exceptionConcept.getUuid());
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.fulfillerStatusConcept.received", receivedConcept.getUuid());
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.labIdentifierConcept", labIdConcept.getUuid());
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.fulfillerEncounterLinkingConcepts", linkingConceptsValue);
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.didNotPerformReason", otherConcept.getUuid());
    }

    private Concept saveConcept(String name) {
        Concept concept = new Concept();
        concept.addName(new ConceptName(name, Locale.ENGLISH));
        concept.setDatatype(Context.getConceptService().getConceptDatatypeByName("N/A"));
        concept.setConceptClass(Context.getConceptService().getConceptClassByName("Misc"));
        return Context.getConceptService().saveConcept(concept);
    }

    private Concept saveConceptCoded(String name) {
        Concept concept = new Concept();
        concept.addName(new ConceptName(name, Locale.ENGLISH));
        concept.setDatatype(Context.getConceptService().getConceptDatatypeByName("Coded"));
        concept.setConceptClass(Context.getConceptService().getConceptClassByName("Question"));
        return Context.getConceptService().saveConcept(concept);
    }

    private Encounter createEncounterForPatient(Patient patient) {
        Encounter encounter = new Encounter();
        encounter.setEncounterDatetime(new Date());
        encounter.setPatient(patient);
        encounter.setEncounterType(Context.getEncounterService().getEncounterType(1));
        encounter.setLocation(getLocation());
        return Context.getEncounterService().saveEncounter(encounter);
    }

    private void saveLinkingObs(Order order, Encounter encounter, Concept concept, Date datetime) {
        Obs obs = new Obs();
        obs.setPerson(order.getPatient());
        obs.setConcept(concept);
        obs.setObsDatetime(datetime);
        obs.setLocation(getLocation());
        obs.setOrder(order);
        obs.setEncounter(encounter);
        obs.setValueCoded(inProgressConcept);
        Context.getObsService().saveObs(obs, "");
    }

    private Obs findStatusObs(Encounter encounter, Order order) {
        Obs latest = null;
        for (Obs obs : encounter.getObsAtTopLevel(false)) {
            if (statusConcept.equals(obs.getConcept()) && order.equals(obs.getOrder())) {
                if (latest == null || isAfterObs(obs, latest)) {
                    latest = obs;
                }
            }
        }
        return latest;
    }

    private boolean isAfterObs(Obs candidate, Obs current) {
        if (candidate.getObsDatetime() == null) return false;
        if (current.getObsDatetime() == null) return true;
        int cmp = candidate.getObsDatetime().compareTo(current.getObsDatetime());
        if (cmp != 0) return cmp > 0;
        if (candidate.getId() != null && current.getId() != null) return candidate.getId() > current.getId();
        return false;
    }

    private long countStatusObs(Encounter encounter, Order order) {
        long count = 0;
        for (Obs obs : encounter.getObsAtTopLevel(false)) {
            if (statusConcept.equals(obs.getConcept()) && order.equals(obs.getOrder())) {
                count++;
            }
        }
        return count;
    }

    private Order getOrder(int id) {
        return Context.getOrderService().getOrder(id);
    }

    private Location getLocation() {
        return Context.getLocationService().getLocation(1);
    }
}
