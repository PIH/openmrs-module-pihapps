package org.openmrs.module.pihapps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEvent;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEventType;
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Covers result validation: submitting results for validation records the submission and leaves the order in
 * progress with its results preliminary, and results only reach clinicians once their order is completed.
 */
public class LabResultValidationTest extends BaseModuleContextSensitiveTest {

    // Standard dataset order 6: a completed test order for CD4 count (5497), patient 2
    private static final String ORDER_6 = "1c96f25c-4949-4f72-9931-d808fbc226de";
    // Standard dataset order 7: a test order for CD4 count (5497), patient 2, whose specimen has been received
    private static final String ORDER_7 = "2c96f25c-4949-4f72-9931-d808fbc226df";
    // Added dataset order 102: collected, in progress
    private static final String ORDER_102 = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    // Added validation dataset order 9201: a test order for panel 9110 (5089, 5497), patient 2, specimen received
    private static final String ORDER_9201 = "9201aaaa-0000-4000-8000-000000009201";

    private static final int WEIGHT = 5089;
    private static final int CD4_COUNT = 5497;
    private static final int PANEL = 9110;
    // A datetime concept standing in for the specimen received date
    private static final int RECEIVED_DATE = 20;

    @Autowired
    @Qualifier("pihappsService")
    private PihAppsService pihAppsService;

    @Autowired
    LabOrderConfig labOrderConfig;

    @BeforeEach
    public void setup() {
        executeDataSet("pihapps_lab_order_test_data.xml");
        executeDataSet("pihapps_lab_validation_test_data.xml");
        // A text concept as the lab identifier question: these encounters do not record a lab id
        setGlobalProperty("pihapps.labs.labIdentifierConcept", "96408258-000b-424e-af1a-403919332938");
        // Settings are cached across tests, so each test starts from validation not being required
        setGlobalProperty("pihapps.labs.requireResultValidation", "false");
    }

    @Test
    public void submit_shouldLeaveOrderInProgressAndRecordSubmitted() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        order = save(order, Order.FulfillerStatus.COMPLETED, result, receivedDateObs(order));

        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
        OrderFulfillmentEvent latest = latestValidationEvent(order);
        assertThat(latest.getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_SUBMITTED));
        assertThat(latest.getCreator(), equalTo(Context.getAuthenticatedUser()));
        assertThat(savedStatus(result), equalTo(Obs.Status.PRELIMINARY));
    }

    @Test
    public void submit_shouldMarkPanelResultsPreliminaryButNotSpecimenObs() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_9201);
        Obs panel = resultObs(order, PANEL, null);
        Obs weight = resultObs(order, WEIGHT, 60.0);
        Obs cd4 = resultObs(order, CD4_COUNT, 300.0);
        panel.addGroupMember(weight);
        panel.addGroupMember(cd4);
        Obs receivedDate = receivedDateObs(order);
        save(order, Order.FulfillerStatus.COMPLETED, panel, receivedDate);

        assertThat(savedStatus(panel), equalTo(Obs.Status.PRELIMINARY));
        assertThat(savedStatus(weight), equalTo(Obs.Status.PRELIMINARY));
        assertThat(savedStatus(cd4), equalTo(Obs.Status.PRELIMINARY));
        assertThat(savedStatus(receivedDate), equalTo(Obs.Status.FINAL));
    }

    @Test
    public void submit_shouldNotRecordSecondSubmittedWhenAlreadyAwaiting() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 310.0));

        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
        assertThat(pihAppsService.getOrderFulfillmentEvents(order), hasSize(1));
    }

    @Test
    public void specimenResave_shouldNotChangeAwaitingOrder() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));
        order = save(order, order.getFulfillerStatus(), receivedDateObs(order));

        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
        assertThat(pihAppsService.getOrderFulfillmentEvents(order), hasSize(1));
    }

    @Test
    public void specimenResave_shouldNotChangeCompletedOrder() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_6);
        order = save(order, order.getFulfillerStatus(), receivedDateObs(order));

        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
        assertThat(pihAppsService.getOrderFulfillmentEvents(order), empty());
    }

    @Test
    public void saveResultsOnCompletedOrder_shouldAmendEditsAndSaveAdditionsAsFinal() {
        Order order = order(ORDER_6);
        Obs original = resultObs(order, CD4_COUNT, 300.0);
        Encounter encounter = encounter(order, original);
        save(encounter, order, Order.FulfillerStatus.COMPLETED);
        assertThat(savedStatus(original), equalTo(Obs.Status.FINAL));

        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        encounter = Context.getEncounterService().getEncounterByUuid(encounter.getUuid());
        order = order(ORDER_6);
        Obs edited = encounter.getAllObs(false).iterator().next();
        edited.setValueNumeric(350.0);
        Obs added = resultObs(order, WEIGHT, 60.0);
        encounter.addObs(added);
        order = save(encounter, order, order.getFulfillerStatus());

        Map<Integer, Obs> obsByConcept = Context.getEncounterService().getEncounterByUuid(encounter.getUuid())
                .getAllObs(false).stream().collect(Collectors.toMap(o -> o.getConcept().getConceptId(), o -> o));
        assertThat(obsByConcept.get(CD4_COUNT).getValueNumeric(), equalTo(350.0));
        assertThat(obsByConcept.get(CD4_COUNT).getStatus(), equalTo(Obs.Status.AMENDED));
        assertThat(obsByConcept.get(WEIGHT).getStatus(), equalTo(Obs.Status.FINAL));
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
        assertThat(pihAppsService.getOrderFulfillmentEvents(order), empty());
    }

    @Test
    public void validationOff_shouldCompleteOrderWithFinalResultsAndNoEvents() {
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        order = save(order, Order.FulfillerStatus.COMPLETED, result, receivedDateObs(order));

        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
        assertThat(savedStatus(result), equalTo(Obs.Status.FINAL));
        assertThat(pihAppsService.getOrderFulfillmentEvents(order), empty());
    }

    @Test
    public void getLabResultConcepts_shouldReturnOrderConceptAndSetMembers() {
        List<Integer> conceptIds = pihAppsService.getLabResultConcepts(order(ORDER_9201)).stream()
                .map(Concept::getConceptId).collect(Collectors.toList());
        assertThat(conceptIds, containsInAnyOrder(PANEL, WEIGHT, CD4_COUNT));
    }

    @Test
    public void getOrderFulfillmentStatusOptions_shouldOnlyOfferAwaitingValidationWhenValidationIsRequired() {
        assertThat(hasAwaitingValidationOption(), equalTo(false));
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        assertThat(hasAwaitingValidationOption(), equalTo(true));
    }

    @Test
    public void getObs_shouldOnlyReturnResultsOfCompletedOrdersWhenAsked() {
        Order completed = Context.getOrderService().getOrder(6);
        Order inProgress = Context.getOrderService().getOrderByUuid(ORDER_102);
        Obs completedResult = saveResult(completed);
        Obs inProgressResult = saveResult(inProgress);
        Obs resultWithoutOrder = saveResult(null);

        ObsSearchCriteria criteria = new ObsSearchCriteria();
        criteria.setPatient(Context.getPatientService().getPatient(2));
        criteria.setConcepts(Collections.singletonList(completedResult.getConcept()));
        criteria.setCreatedOnOrAfter(completedResult.getDateCreated());
        assertThat(obsIds(criteria), containsInAnyOrder(completedResult.getObsId(), inProgressResult.getObsId(),
                resultWithoutOrder.getObsId()));

        criteria.setOnlyCompletedOrders(true);
        assertThat(obsIds(criteria), containsInAnyOrder(completedResult.getObsId(), resultWithoutOrder.getObsId()));
    }

    /**
     * Saves the given obs in a new encounter fulfilling the order, requesting the given fulfiller status
     */
    private Order save(Order order, Order.FulfillerStatus status, Obs... obs) {
        return save(encounter(order, obs), order, status);
    }

    private Order save(Encounter encounter, Order order, Order.FulfillerStatus status) {
        order.setFulfillerStatus(status);
        EncounterFulfillingOrders encounterFulfillingOrders = new EncounterFulfillingOrders();
        encounterFulfillingOrders.setEncounter(encounter);
        encounterFulfillingOrders.setOrders(Collections.singletonList(order));
        pihAppsService.saveEncounterFulfillingOrders(encounterFulfillingOrders);
        Context.flushSession();
        Context.clearSession();
        return order(order.getUuid());
    }

    private Encounter encounter(Order order, Obs... obs) {
        Encounter encounter = new Encounter();
        encounter.setPatient(order.getPatient());
        encounter.setEncounterType(Context.getEncounterService().getEncounterType(1));
        encounter.setLocation(Context.getLocationService().getLocation(1));
        encounter.setEncounterDatetime(new Date());
        for (Obs o : obs) {
            encounter.addObs(o);
        }
        return encounter;
    }

    private Obs resultObs(Order order, int conceptId, Double value) {
        Obs obs = new Obs();
        obs.setPerson(order.getPatient());
        obs.setConcept(Context.getConceptService().getConcept(conceptId));
        obs.setObsDatetime(new Date());
        obs.setValueNumeric(value);
        obs.setOrder(order);
        return obs;
    }

    private Obs receivedDateObs(Order order) {
        Obs obs = new Obs();
        obs.setPerson(order.getPatient());
        obs.setConcept(Context.getConceptService().getConcept(RECEIVED_DATE));
        obs.setObsDatetime(new Date());
        obs.setValueDatetime(new Date());
        obs.setOrder(order);
        return obs;
    }

    private Obs.Status savedStatus(Obs obs) {
        return Context.getObsService().getObsByUuid(obs.getUuid()).getStatus();
    }

    private OrderFulfillmentEvent latestValidationEvent(Order order) {
        return pihAppsService.getLatestOrderFulfillmentEvents(Collections.singletonList(order),
                OrderFulfillmentEventType.RESULT_VALIDATION).get(order);
    }

    private Order order(String uuid) {
        return Context.getOrderService().getOrderByUuid(uuid);
    }

    private boolean hasAwaitingValidationOption() {
        return labOrderConfig.getOrderFulfillmentStatusOptions().stream()
                .anyMatch(o -> OrderFulfillmentStatus.AWAITING_VALIDATION.name().equals(o.get("status")));
    }

    private Obs saveResult(Order order) {
        Concept weight = Context.getConceptService().getConcept(WEIGHT);
        Obs obs = new Obs();
        obs.setPerson(Context.getPersonService().getPerson(2));
        obs.setConcept(weight);
        obs.setObsDatetime(new Date());
        obs.setValueNumeric(60.0);
        obs.setOrder(order);
        return Context.getObsService().saveObs(obs, null);
    }

    private List<Integer> obsIds(ObsSearchCriteria criteria) {
        return pihAppsService.getObs(criteria).getObs().stream().map(Obs::getObsId).collect(Collectors.toList());
    }

    private void setGlobalProperty(String property, String value) {
        Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(property, value));
    }
}
