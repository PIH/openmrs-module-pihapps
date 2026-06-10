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

import java.util.Date;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class PihAppsServiceFulfillerEncounterTest extends BaseModuleContextSensitiveTest {

    @Autowired
    @Qualifier("pihappsService")
    private PihAppsService pihAppsService;

    private Concept statusConcept;
    private Concept inProgressConcept;
    private Concept otherConcept;

    @BeforeEach
    public void setup() throws Exception {
        executeDataSet("pihapps_lab_order_test_data.xml");

        statusConcept     = saveConceptCoded("Test Status");
        inProgressConcept = saveConcept("In Progress");
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

    // ==================== helpers ====================

    private void configureGps(String linkingConceptsValue) {
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.fulfillerEncounterLinkingConcepts", linkingConceptsValue);
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

    private Order getOrder(int id) {
        return Context.getOrderService().getOrder(id);
    }

    private Location getLocation() {
        return Context.getLocationService().getLocation(1);
    }
}
