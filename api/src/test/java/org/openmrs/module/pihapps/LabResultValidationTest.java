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
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Covers result validation: saving results leaves the order awaiting validation when validation is required,
 * the order list can find those orders, and results only reach clinicians once their order is completed.
 */
public class LabResultValidationTest extends BaseModuleContextSensitiveTest {

    // Standard dataset order 7: a test order for patient 2 whose specimen has been received
    private static final String ORDER_7 = "2c96f25c-4949-4f72-9931-d808fbc226df";
    // Added dataset orders 102 and 103: collected, in progress
    private static final String ORDER_102 = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    private static final String ORDER_103 = "c3d4e5f6-a7b8-9012-cdef-123456789012";

    @Autowired
    @Qualifier("pihappsService")
    private PihAppsService pihAppsService;

    @Autowired
    LabOrderConfig labOrderConfig;

    @BeforeEach
    public void setup() {
        executeDataSet("pihapps_lab_order_test_data.xml");
        // Any concept will do as the lab identifier question: these encounters do not record a lab id
        setGlobalProperty("pihapps.labs.labIdentifierConcept", "a09ab2c5-878e-4905-b25d-5784167d0216");
        // Settings are cached across tests, so each test starts from validation not being required
        setGlobalProperty("pihapps.labs.requireResultValidation", "false");
    }

    @Test
    public void saveEncounterFulfillingOrders_shouldLeaveCompletedOrderAwaitingValidationWhenValidationIsRequired() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = saveWithStatus(ORDER_7, Order.FulfillerStatus.COMPLETED);
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
        assertThat(order.getFulfillerComment(), equalTo(OrderFulfillmentStatus.RESULTS_AWAITING_VALIDATION));
    }

    @Test
    public void saveEncounterFulfillingOrders_shouldCompleteOrderWhenValidationIsNotRequired() {
        Order order = saveWithStatus(ORDER_7, Order.FulfillerStatus.COMPLETED);
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
        assertThat(order.getFulfillerComment(), nullValue());
    }

    @Test
    public void saveEncounterFulfillingOrders_shouldNoLongerAwaitValidationWhenSavedAsInProgress() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        saveWithStatus(ORDER_7, Order.FulfillerStatus.COMPLETED);
        Order order = saveWithStatus(ORDER_7, Order.FulfillerStatus.IN_PROGRESS);
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
        assertThat(order.getFulfillerComment(), nullValue());
    }

    @Test
    public void getOrders_shouldSeparateOrdersAwaitingValidationFromOrdersInFulfillment() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        saveWithStatus(ORDER_7, Order.FulfillerStatus.COMPLETED);
        assertThat(orderUuids(OrderFulfillmentStatus.AWAITING_VALIDATION), contains(ORDER_7));
        assertThat(orderUuids(OrderFulfillmentStatus.IN_FULFILLMENT), containsInAnyOrder(ORDER_102, ORDER_103));
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

    private Order saveWithStatus(String orderUuid, Order.FulfillerStatus status) {
        Order order = Context.getOrderService().getOrderByUuid(orderUuid);
        order.setFulfillerStatus(status);
        EncounterFulfillingOrders encounterFulfillingOrders = new EncounterFulfillingOrders();
        encounterFulfillingOrders.setEncounter(specimenEncounter(order));
        encounterFulfillingOrders.setOrders(Collections.singletonList(order));
        pihAppsService.saveEncounterFulfillingOrders(encounterFulfillingOrders);
        Context.flushSession();
        return Context.getOrderService().getOrderByUuid(orderUuid);
    }

    private Encounter specimenEncounter(Order order) {
        Encounter encounter = new Encounter();
        encounter.setPatient(order.getPatient());
        encounter.setEncounterType(Context.getEncounterService().getEncounterType(1));
        encounter.setLocation(Context.getLocationService().getLocation(1));
        encounter.setEncounterDatetime(new Date());
        return encounter;
    }

    private List<String> orderUuids(OrderFulfillmentStatus status) {
        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));
        criteria.setOrderFulfillmentStatuses(Collections.singletonList(status));
        OrderSearchResult result = pihAppsService.getOrders(criteria);
        return result.getOrders().stream().map(Order::getUuid).collect(Collectors.toList());
    }

    private boolean hasAwaitingValidationOption() {
        return labOrderConfig.getOrderFulfillmentStatusOptions().stream()
                .anyMatch(o -> OrderFulfillmentStatus.AWAITING_VALIDATION.name().equals(o.get("status")));
    }

    private Obs saveResult(Order order) {
        Concept weight = Context.getConceptService().getConcept(5089);
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
