package org.openmrs.module.pihapps;

import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Privilege;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEvent;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEventType;
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers result validation: submitting results for validation records the submission and leaves the order in
 * progress with its results preliminary, a validator who did not enter the results validates them or returns them
 * for correction, and results only reach clinicians once their order is completed.
 */
public class LabResultValidationTest extends BaseModuleContextSensitiveTest {

    // Standard dataset order 6: a completed test order for CD4 count (5497), patient 2
    private static final String ORDER_6 = "1c96f25c-4949-4f72-9931-d808fbc226de";
    // Standard dataset order 7: a test order for CD4 count (5497), patient 2, whose specimen has been received
    private static final String ORDER_7 = "2c96f25c-4949-4f72-9931-d808fbc226df";
    // Added dataset order 102: collected, in progress
    private static final String ORDER_102 = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    // Added validation dataset order 9201: a test order for panel 9110 (5089, 5497), patient 2, specimen received
    // Added dataset order 103: collected, in progress
    private static final String ORDER_103 = "c3d4e5f6-a7b8-9012-cdef-123456789012";
    private static final String ORDER_9201 = "9201aaaa-0000-4000-8000-000000009201";

    // Standard dataset user who enters no results
    private static final int VALIDATOR_USER = 502;

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
    public void markNotPerformed_shouldChangeCompletedOrderToException() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_6);
        order = save(order, Order.FulfillerStatus.EXCEPTION, receivedDateObs(order));

        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.EXCEPTION));
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
        // The change is recorded, without changing whether the order awaits validation
        assertThat(eventTypes(order), contains(OrderFulfillmentEventType.RESULTS_AMENDED));
        assertThat(latestValidationEvent(order), nullValue());

        // Saving the completed order again without changing its results records nothing more
        save(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order, order.getFulfillerStatus());
        assertThat(eventTypes(order(ORDER_6)), contains(OrderFulfillmentEventType.RESULTS_AMENDED));
    }

    @Test
    public void validate_shouldRejectResultsChangedSinceTheValidatorReviewedThem() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        Encounter encounter = encounter(order, result);
        order = save(encounter, order, Order.FulfillerStatus.COMPLETED);
        List<String> reviewed = pihAppsService.getLabResultObs(order).stream().map(Obs::getUuid).collect(Collectors.toList());

        // The lab corrects the result while it awaits validation
        order = order(ORDER_7);
        encounter = Context.getEncounterService().getEncounterByUuid(encounter.getUuid());
        encounter.getAllObs(false).stream().filter(o -> o.getConcept().getConceptId() == CD4_COUNT).findFirst().get().setValueNumeric(30.0);
        order = save(encounter, order, Order.FulfillerStatus.COMPLETED);

        becomeValidator();
        Order awaiting = order;
        APIException e = assertThrows(APIException.class, () -> pihAppsService.validateLabResults(Collections.singletonList(awaiting),
                Collections.singletonMap(awaiting, reviewed), null));
        assertThat(e.getMessage().contains(awaiting.getOrderNumber()), equalTo(true));
        Context.flushSession();
        Context.clearSession();
        assertThat(order(ORDER_7).getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));

        // Reviewing the corrected result, it can be validated
        validate(Collections.singletonList(order(ORDER_7)), null);
        Context.flushSession();
        Context.clearSession();
        assertThat(order(ORDER_7).getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
    }

    @Test
    public void submittedResults_shouldBeWithdrawnWhenTheOrderLeavesInProgress() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Encounter encounter = encounter(order, resultObs(order, CD4_COUNT, 300.0));
        order = save(encounter, order, Order.FulfillerStatus.COMPLETED);

        // Not performed, then in progress again: the old submission does not await validation
        order = save(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order, Order.FulfillerStatus.EXCEPTION);
        assertThat(latestValidationEvent(order).getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_WITHDRAWN));
        order = save(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order, Order.FulfillerStatus.IN_PROGRESS);
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
        assertThat(orderUuids(OrderFulfillmentStatus.AWAITING_VALIDATION), empty());
        assertThat(pihAppsService.canValidateLabResults(order, validator()), equalTo(false));

        // Submitted again, it awaits validation
        save(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order, Order.FulfillerStatus.COMPLETED);
        assertThat(orderUuids(OrderFulfillmentStatus.AWAITING_VALIDATION), containsInAnyOrder(ORDER_7));
    }

    @Test
    public void submittedResults_shouldBeWithdrawnWhenMarkedNotPerformedOrReverted() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));
        pihAppsService.markOrdersAsNotFulfilled(Collections.singletonList(order), null);
        Context.flushSession();
        assertThat(latestValidationEvent(order(ORDER_7)).getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_WITHDRAWN));

        order = order(ORDER_9201);
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, WEIGHT, 60.0));
        pihAppsService.revertOrdersToOrdered(Collections.singletonList(order));
        Context.flushSession();
        assertThat(latestValidationEvent(order(ORDER_9201)).getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_WITHDRAWN));
    }

    @Test
    public void canValidate_shouldBeFalseForTheUserWhoSubmittedResultsSomeoneElseEntered() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        User admin = Context.getAuthenticatedUser();
        // Another user entered a result, eg a lab machine's user, left in progress
        User enteredBy = validator();
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        result.setCreator(enteredBy);
        Encounter encounter = encounter(order, result);
        save(encounter, order, Order.FulfillerStatus.IN_PROGRESS);

        // Admin submits it without changing it
        order = order(ORDER_7);
        order = save(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order, Order.FulfillerStatus.COMPLETED);

        assertThat(pihAppsService.getLabResultEditors(order), containsInAnyOrder(enteredBy, admin));
        assertThat(pihAppsService.canValidateLabResults(order, admin), equalTo(false));
    }

    @Test
    public void saveOrderFulfillmentEvent_shouldRequireTheValidatePrivilege() {
        Order order = order(ORDER_7);
        User user = Context.getUserService().getUser(VALIDATOR_USER); // without the validator role in this test
        Context.becomeUser(user.getSystemId());
        Context.addProxyPrivilege(PrivilegeConstants.EDIT_ORDERS);
        for (OrderFulfillmentEventType type : OrderFulfillmentEventType.values()) {
            OrderFulfillmentEvent event = new OrderFulfillmentEvent();
            event.setOrder(order);
            event.setEventType(type);
            assertThrows(APIAuthenticationException.class, () -> pihAppsService.saveOrderFulfillmentEvent(event));
        }
    }

    @Test
    public void saveEditorResult_shouldRejectAnExistingGroupGivenBack() {
        Order order = order(ORDER_9201);
        Encounter encounter = encounter(order);
        order = saveWithGroupResult(encounter, order, Order.FulfillerStatus.COMPLETED, panelGroup(60.0, 300.0));
        Obs existing = groupResult(order);
        Order completed = order;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> saveWithGroupResult(
                Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), completed, Order.FulfillerStatus.COMPLETED, existing));
        assertThat(e.getMessage().contains("must be new obs"), equalTo(true));
        Context.clearSession();
        assertThat(groupResult(order(ORDER_9201)).getUuid(), equalTo(existing.getUuid()));
    }

    @Test
    public void saveEditorResult_shouldSaveTheGroupAsResultsOfTheOrder() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_9201);
        Encounter encounter = encounter(order);
        order = saveWithGroupResult(encounter, order, Order.FulfillerStatus.COMPLETED, panelGroup(60.0, 300.0));

        Obs group = groupResult(order);
        assertThat(group.getGroupMembers(), hasSize(2));
        for (Obs obs : Arrays.asList(group, group.getGroupMembers().iterator().next())) {
            assertThat(obs.getOrder(), equalTo(order));
            assertThat(obs.getEncounter().getUuid(), equalTo(encounter.getUuid()));
            assertThat(obs.getStatus(), equalTo(Obs.Status.PRELIMINARY));
        }
        assertThat(orderUuids(OrderFulfillmentStatus.AWAITING_VALIDATION), hasItem(ORDER_9201));

        // Validated with the order's other results
        becomeValidator();
        validate(Collections.singletonList(order), null);
        Context.flushSession();
        Context.clearSession();
        assertThat(groupResult(order(ORDER_9201)).getGroupMembers().stream().allMatch(o -> o.getStatus() == Obs.Status.FINAL), equalTo(true));
    }

    @Test
    public void saveEditorResult_shouldReplaceTheGroupAndAmendAFinalOne() {
        Order order = order(ORDER_9201);
        Encounter encounter = encounter(order);
        order = saveWithGroupResult(encounter, order, Order.FulfillerStatus.COMPLETED, panelGroup(60.0, 300.0));
        Obs first = groupResult(order);
        assertThat(first.getStatus(), equalTo(Obs.Status.FINAL));

        order = saveWithGroupResult(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order,
                Order.FulfillerStatus.COMPLETED, panelGroup(61.0, 310.0));
        Obs second = groupResult(order);
        assertThat(second.getUuid().equals(first.getUuid()), equalTo(false));
        assertThat(second.getPreviousVersion().getUuid(), equalTo(first.getUuid()));
        assertThat(second.getStatus(), equalTo(Obs.Status.AMENDED));
        Obs voided = Context.getObsService().getObsByUuid(first.getUuid());
        assertThat(voided.getVoided(), equalTo(true));
        assertThat(voided.getGroupMembers(true).stream().allMatch(Obs::getVoided), equalTo(true));

        // No obs removes it
        order = saveWithGroupResult(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order,
                Order.FulfillerStatus.COMPLETED, null);
        assertThat(groupResult(order), nullValue());
    }

    @Test
    public void saveEditorResult_shouldReplaceTheGroupWhenTheEncounterGivenDoesNotHoldIt() {
        Order order = order(ORDER_9201);
        Encounter encounter = encounter(order);
        order = saveWithGroupResult(encounter, order, Order.FulfillerStatus.COMPLETED, panelGroup(60.0, 300.0));
        Obs first = groupResult(order);

        // As a request converted to an encounter that only holds the obs it sends
        Encounter given = Context.getEncounterService().getEncounterByUuid(encounter.getUuid());
        given.setObs(new java.util.LinkedHashSet<>());
        order = saveWithGroupResult(given, order, Order.FulfillerStatus.COMPLETED, panelGroup(61.0, 310.0));

        List<Obs> groups = pihAppsService.getLabResultObs(order).stream()
                .filter(o -> o.getConcept().getConceptId() == PANEL).collect(Collectors.toList());
        assertThat(groups, hasSize(1));
        assertThat(groups.get(0).getPreviousVersion().getUuid(), equalTo(first.getUuid()));
        assertThat(groups.get(0).getStatus(), equalTo(Obs.Status.AMENDED));
    }

    @Test
    public void saveEditorResult_shouldSaveAndReplaceASingleObs() {
        Order order = order(ORDER_7);
        Encounter encounter = encounter(order);
        order = saveWithEditorResult(encounter, order, Order.FulfillerStatus.COMPLETED, CD4_COUNT, cd4(300.0));
        List<Obs> results = cd4Results(order);
        assertThat(results, hasSize(1));
        Obs first = results.get(0);
        assertThat(first.getValueNumeric(), equalTo(300.0));
        assertThat(first.getOrder(), equalTo(order));
        assertThat(first.getStatus(), equalTo(Obs.Status.FINAL));

        order = saveWithEditorResult(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order,
                Order.FulfillerStatus.COMPLETED, CD4_COUNT, cd4(310.0));
        results = cd4Results(order);
        assertThat(results, hasSize(1));
        assertThat(results.get(0).getValueNumeric(), equalTo(310.0));
        assertThat(results.get(0).getPreviousVersion().getUuid(), equalTo(first.getUuid()));
        assertThat(results.get(0).getStatus(), equalTo(Obs.Status.AMENDED));
    }

    @Test
    public void saveEditorResult_shouldPutTheResultOfAPanelMemberInTheGroupOfThePanel() {
        Order order = order(ORDER_9201);
        Encounter encounter = encounter(order);
        order = saveWithEditorResult(encounter, order, Order.FulfillerStatus.COMPLETED, CD4_COUNT, cd4(300.0));
        Obs cd4 = cd4Results(order).get(0);
        Obs panelGroup = cd4.getObsGroup();
        assertThat(panelGroup.getConcept().getConceptId(), equalTo(PANEL));
        assertThat(panelGroup.getOrder(), equalTo(order));
        assertThat(panelGroup.getObsGroup(), nullValue());

        // Another member's result goes in the same group, and a replaced result is replaced within it
        Obs weight = cd4(70.0);
        weight.setConcept(Context.getConceptService().getConcept(WEIGHT));
        order = saveWithEditorResult(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order,
                Order.FulfillerStatus.COMPLETED, WEIGHT, weight);
        order = saveWithEditorResult(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order,
                Order.FulfillerStatus.COMPLETED, CD4_COUNT, cd4(310.0));
        Obs group = Context.getObsService().getObsByUuid(panelGroup.getUuid());
        assertThat(group.getVoided(), equalTo(false));
        assertThat(group.getGroupMembers(false).stream().map(m -> m.getConcept().getConceptId() + "=" + m.getValueNumeric())
                .collect(Collectors.toSet()), equalTo(new java.util.HashSet<>(Arrays.asList(CD4_COUNT + "=310.0", WEIGHT + "=70.0"))));

        // Removing the results voids the group once it has none
        order = saveWithEditorResult(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order,
                Order.FulfillerStatus.COMPLETED, CD4_COUNT, null);
        assertThat(Context.getObsService().getObsByUuid(panelGroup.getUuid()).getVoided(), equalTo(false));
        saveWithEditorResult(Context.getEncounterService().getEncounterByUuid(encounter.getUuid()), order,
                Order.FulfillerStatus.COMPLETED, WEIGHT, null);
        assertThat(Context.getObsService().getObsByUuid(panelGroup.getUuid()).getVoided(), equalTo(true));
    }

    private Obs cd4(Double value) {
        Obs obs = new Obs();
        obs.setConcept(Context.getConceptService().getConcept(CD4_COUNT));
        obs.setValueNumeric(value);
        return obs;
    }

    private List<Obs> cd4Results(Order order) {
        return pihAppsService.getLabResultObs(order).stream()
                .filter(o -> o.getConcept().getConceptId() == CD4_COUNT).collect(Collectors.toList());
    }

    @Test
    public void saveEditorResult_shouldRejectAConceptThatIsNotAResultOfTheOrder() {
        Order order = order(ORDER_7);
        EncounterFulfillingOrders encounterFulfillingOrders = new EncounterFulfillingOrders();
        encounterFulfillingOrders.setEncounter(encounter(order));
        encounterFulfillingOrders.setOrders(Collections.singletonList(order));
        EncounterFulfillingOrders.EditorResult result = new EncounterFulfillingOrders.EditorResult();
        result.setOrder(order);
        result.setConcept(Context.getConceptService().getConcept(PANEL));
        result.setObs(panelGroup(60.0, 300.0));
        encounterFulfillingOrders.setEditorResults(Collections.singletonList(result));
        assertThrows(IllegalArgumentException.class, () -> pihAppsService.saveEncounterFulfillingOrders(encounterFulfillingOrders));
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
    public void getOrders_shouldSeparateAwaitingValidationFromCollected() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));

        assertThat(orderUuids(OrderFulfillmentStatus.AWAITING_VALIDATION), containsInAnyOrder(ORDER_7));
        assertThat(orderUuids(OrderFulfillmentStatus.IN_FULFILLMENT), containsInAnyOrder(ORDER_102, ORDER_103));
    }

    @Test
    public void getOrders_shouldShowReturnedOrdersAsCollected() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));
        saveEvent(order, OrderFulfillmentEventType.RESULTS_RETURNED);

        assertThat(orderUuids(OrderFulfillmentStatus.IN_FULFILLMENT), hasItem(ORDER_7));
        assertThat(orderUuids(OrderFulfillmentStatus.AWAITING_VALIDATION), empty());
    }

    @Test
    public void getOrders_shouldKeepOrdersAwaitingValidationWhenAnEarlierEventIsRecordedAfterTheSubmission() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));
        OrderFulfillmentEvent returned = new OrderFulfillmentEvent();
        returned.setOrder(order);
        returned.setEventType(OrderFulfillmentEventType.RESULTS_RETURNED);
        returned.setEventDatetime(new Date(latestValidationEvent(order).getEventDatetime().getTime() - 60000));
        pihAppsService.saveOrderFulfillmentEvent(returned);
        Context.flushSession();

        assertThat(orderUuids(OrderFulfillmentStatus.AWAITING_VALIDATION), containsInAnyOrder(ORDER_7));
    }

    @Test
    public void getOrders_shouldIgnoreVoidedSubmission() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));
        OrderFulfillmentEvent event = latestValidationEvent(order);
        event.setVoided(true);
        pihAppsService.saveOrderFulfillmentEvent(event);
        Context.flushSession();

        assertThat(orderUuids(OrderFulfillmentStatus.IN_FULFILLMENT), hasItem(ORDER_7));
        assertThat(orderUuids(OrderFulfillmentStatus.AWAITING_VALIDATION), empty());
    }

    @Test
    public void getOrders_shouldShowSubmittedOrdersAsCollectedWhenValidationTurnedOff() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));
        setGlobalProperty("pihapps.labs.requireResultValidation", "false");

        assertThat(orderUuids(OrderFulfillmentStatus.IN_FULFILLMENT), hasItem(ORDER_7));
    }

    @Test
    public void getObs_shouldFilterByStatus() {
        Order order = Context.getOrderService().getOrder(6);
        Obs preliminary = saveResult(order, Obs.Status.PRELIMINARY);
        Obs finalResult = saveResult(order, Obs.Status.FINAL);
        Obs amended = saveResult(order, Obs.Status.AMENDED);

        ObsSearchCriteria criteria = new ObsSearchCriteria();
        criteria.setPatient(Context.getPatientService().getPatient(2));
        criteria.setConcepts(Collections.singletonList(finalResult.getConcept()));
        criteria.setCreatedOnOrAfter(preliminary.getDateCreated());
        assertThat(obsIds(criteria), containsInAnyOrder(preliminary.getObsId(), finalResult.getObsId(),
                amended.getObsId()));

        criteria.setStatuses(Arrays.asList(Obs.Status.FINAL, Obs.Status.AMENDED));
        assertThat(obsIds(criteria), containsInAnyOrder(finalResult.getObsId(), amended.getObsId()));
    }

    @Test
    public void validate_shouldFinalizeResultsInPlaceAndCompleteOrder() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        Encounter encounter = encounter(order, result);
        save(encounter, order, Order.FulfillerStatus.COMPLETED);
        Set<Integer> obsIdsBefore = encounterObsIds(encounter);
        Integer resultId = Context.getObsService().getObsByUuid(result.getUuid()).getObsId();

        User validator = becomeValidator();
        validate(Collections.singletonList(order(ORDER_7)), "Looks right");
        Context.flushSession();
        Context.clearSession();

        Obs saved = Context.getObsService().getObsByUuid(result.getUuid());
        assertThat(saved.getObsId(), equalTo(resultId));
        assertThat(saved.getStatus(), equalTo(Obs.Status.FINAL));
        assertThat(encounterObsIds(encounter), equalTo(obsIdsBefore));
        order = order(ORDER_7);
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
        OrderFulfillmentEvent latest = latestValidationEvent(order);
        assertThat(latest.getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_VALIDATED));
        assertThat(latest.getCreator(), equalTo(validator));
        assertThat(latest.getComment(), equalTo("Looks right"));
    }

    @Test
    public void validate_shouldFinalizePanelResults() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_9201);
        Obs panel = resultObs(order, PANEL, null);
        Obs weight = resultObs(order, WEIGHT, 60.0);
        Obs cd4 = resultObs(order, CD4_COUNT, 300.0);
        panel.addGroupMember(weight);
        panel.addGroupMember(cd4);
        Obs receivedDate = receivedDateObs(order);
        save(order, Order.FulfillerStatus.COMPLETED, panel, receivedDate);

        becomeValidator();
        validate(Collections.singletonList(order(ORDER_9201)), null);
        Context.flushSession();
        Context.clearSession();

        assertThat(savedStatus(panel), equalTo(Obs.Status.FINAL));
        assertThat(savedStatus(weight), equalTo(Obs.Status.FINAL));
        assertThat(savedStatus(cd4), equalTo(Obs.Status.FINAL));
        assertThat(savedStatus(receivedDate), equalTo(Obs.Status.FINAL));
        assertThat(order(ORDER_9201).getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
    }

    @Test
    public void submitAndValidate_shouldTreatMembersOfResultGroupsAsResults() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_9201);
        Obs panel = resultObs(order, PANEL, null);
        Obs weight = resultObs(order, WEIGHT, 60.0);
        // A group within the panel's group with a member whose concept is not a set member of the panel, as an
        // implementation's own result widget may record
        Obs nestedGroup = resultObs(order, PANEL, null);
        Obs nonMember = receivedDateObs(order);
        // Members of a group are not always linked to the order themselves
        nonMember.setOrder(null);
        nestedGroup.addGroupMember(nonMember);
        panel.addGroupMember(weight);
        panel.addGroupMember(nestedGroup);
        save(order, Order.FulfillerStatus.COMPLETED, panel);
        assertThat(savedStatus(nonMember), equalTo(Obs.Status.PRELIMINARY));

        becomeValidator();
        validate(Collections.singletonList(order(ORDER_9201)), null);
        Context.flushSession();
        Context.clearSession();
        assertThat(savedStatus(nonMember), equalTo(Obs.Status.FINAL));
    }

    @Test
    public void getNewLabResultStatus_shouldBePreliminaryUntilTheOrderIsReleased() {
        Order inProgress = order(ORDER_102);
        Order completed = order(ORDER_6);
        assertThat(pihAppsService.getNewLabResultStatus(inProgress), equalTo(Obs.Status.FINAL));

        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        assertThat(pihAppsService.getNewLabResultStatus(inProgress), equalTo(Obs.Status.PRELIMINARY));
        assertThat(pihAppsService.getNewLabResultStatus(completed), equalTo(Obs.Status.FINAL));
        inProgress.setFulfillerStatus(Order.FulfillerStatus.EXCEPTION);
        assertThat(pihAppsService.getNewLabResultStatus(inProgress), equalTo(Obs.Status.FINAL));
    }

    @Test
    public void validate_shouldRejectUserWhoEnteredResults() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        save(order, Order.FulfillerStatus.COMPLETED, result);

        // The authenticated user entered the results
        assertThat(pihAppsService.canValidateLabResults(order(ORDER_7), Context.getAuthenticatedUser()), equalTo(false));
        assertThrows(APIException.class,
                () -> validate(Collections.singletonList(order(ORDER_7)), null));
        assertAwaitingWithPreliminaryResult(result);
    }

    @Test
    public void validate_shouldRejectUserWhoVoidedAResult() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        Obs removed = resultObs(order, CD4_COUNT, 310.0);
        save(order, Order.FulfillerStatus.COMPLETED, result, removed);
        User validator = validator();
        executeSql("update obs set voided = true, voided_by = " + validator.getUserId() + ", date_voided = now(), "
                + "void_reason = 'test' where uuid = '" + removed.getUuid() + "'");

        assertThat(pihAppsService.getLabResultEditors(order(ORDER_7)), hasItem(validator));
        becomeValidator();
        assertThrows(APIException.class,
                () -> validate(Collections.singletonList(order(ORDER_7)), null));
        assertAwaitingWithPreliminaryResult(result);
    }

    @Test
    public void validate_shouldAllowUserWhoOnlyLastSavedTheEncounter() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        Encounter encounter = encounter(order, result);
        save(encounter, order, Order.FulfillerStatus.COMPLETED);
        // Eg the specimen collection was edited: that is not entering the results
        User validator = validator();
        executeSql("update encounter set changed_by = " + validator.getUserId() + ", date_changed = now() "
                + "where uuid = '" + encounter.getUuid() + "'");

        assertThat(pihAppsService.getLabResultEditors(order(ORDER_7)).contains(validator), equalTo(false));
        becomeValidator();
        validate(Collections.singletonList(order(ORDER_7)), null);
        Context.flushSession();
        Context.clearSession();
        assertThat(order(ORDER_7).getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
    }

    @Test
    public void validate_shouldRejectOrderNotAwaitingValidation() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        // Returned for correction
        Order returned = order(ORDER_7);
        save(returned, Order.FulfillerStatus.COMPLETED, resultObs(returned, CD4_COUNT, 300.0));
        saveEvent(order(ORDER_7), OrderFulfillmentEventType.RESULTS_RETURNED);
        // Marked not performed after submission
        Order notPerformed = order(ORDER_9201);
        save(notPerformed, Order.FulfillerStatus.COMPLETED, resultObs(notPerformed, WEIGHT, 60.0));
        pihAppsService.markOrdersAsNotFulfilled(Collections.singletonList(order(ORDER_9201)), null);
        Context.flushSession();
        Context.clearSession();

        User validator = becomeValidator();
        for (String uuid : Arrays.asList(ORDER_7, ORDER_9201, ORDER_6)) {
            assertThat(uuid, pihAppsService.canValidateLabResults(order(uuid), validator), equalTo(false));
            APIException e = assertThrows(APIException.class,
                    () -> validate(Collections.singletonList(order(uuid)), null));
            assertThat(e.getMessage().contains(order(uuid).getOrderNumber()), equalTo(true));
        }
    }

    @Test
    public void validate_shouldChangeNothingIfAnyOrderCannotBeValidated() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        save(order, Order.FulfillerStatus.COMPLETED, result);

        User validator = becomeValidator();
        assertThat(pihAppsService.canValidateLabResults(order(ORDER_7), validator), equalTo(true));
        assertThrows(APIException.class,
                () -> validate(Arrays.asList(order(ORDER_7), order(ORDER_6)), null));
        assertAwaitingWithPreliminaryResult(result);
    }

    @Test
    public void validate_shouldRequirePrivilege() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 300.0));
        Order awaiting = order(ORDER_7);

        Context.becomeUser(Context.getUserService().getUser(VALIDATOR_USER).getSystemId());
        assertThrows(APIAuthenticationException.class,
                () -> validate(Collections.singletonList(awaiting), null));
        assertThrows(APIAuthenticationException.class,
                () -> pihAppsService.returnLabResultsForCorrection(Collections.singletonList(awaiting), "Wrong"));
    }

    @Test
    public void returnForCorrection_shouldRecordReasonAndLeaveOrderWithLab() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        save(order, Order.FulfillerStatus.COMPLETED, result);

        User validator = becomeValidator();
        pihAppsService.returnLabResultsForCorrection(Collections.singletonList(order(ORDER_7)), "  Value too high  ");
        Context.flushSession();
        Context.clearSession();

        order = order(ORDER_7);
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
        OrderFulfillmentEvent latest = latestValidationEvent(order);
        assertThat(latest.getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_RETURNED));
        assertThat(latest.getComment(), equalTo("Value too high"));
        assertThat(latest.getCreator(), equalTo(validator));
        assertThat(savedStatus(result), equalTo(Obs.Status.PRELIMINARY));
        assertThat(pihAppsService.canValidateLabResults(order, validator), equalTo(false));

        // The lab corrects and resubmits
        Context.logout();
        authenticate();
        order = save(order, Order.FulfillerStatus.COMPLETED, resultObs(order, CD4_COUNT, 200.0));
        assertThat(latestValidationEvent(order).getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_SUBMITTED));
        assertThat(pihAppsService.getOrderFulfillmentEvents(order), hasSize(3));
    }

    @Test
    public void returnForCorrection_shouldRequireReason() {
        setGlobalProperty("pihapps.labs.requireResultValidation", "true");
        Order order = order(ORDER_7);
        Obs result = resultObs(order, CD4_COUNT, 300.0);
        save(order, Order.FulfillerStatus.COMPLETED, result);

        becomeValidator();
        assertThrows(APIException.class,
                () -> pihAppsService.returnLabResultsForCorrection(Collections.singletonList(order(ORDER_7)), "  "));
        assertThrows(APIException.class,
                () -> pihAppsService.returnLabResultsForCorrection(Collections.singletonList(order(ORDER_7)), null));
        assertThat(latestValidationEvent(order(ORDER_7)).getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_SUBMITTED));

        String reason = StringUtils.repeat("x", 2000);
        pihAppsService.returnLabResultsForCorrection(Collections.singletonList(order(ORDER_7)), reason);
        Context.flushSession();
        Context.clearSession();
        assertThat(latestValidationEvent(order(ORDER_7)).getComment(), equalTo(reason));
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

    private Order saveWithGroupResult(Encounter encounter, Order order, Order.FulfillerStatus status, Obs group) {
        return saveWithEditorResult(encounter, order, status, PANEL, group);
    }

    private Order saveWithEditorResult(Encounter encounter, Order order, Order.FulfillerStatus status, Integer conceptId, Obs obs) {
        order.setFulfillerStatus(status);
        EncounterFulfillingOrders encounterFulfillingOrders = new EncounterFulfillingOrders();
        encounterFulfillingOrders.setEncounter(encounter);
        encounterFulfillingOrders.setOrders(Collections.singletonList(order));
        EncounterFulfillingOrders.EditorResult result = new EncounterFulfillingOrders.EditorResult();
        result.setOrder(order);
        result.setConcept(Context.getConceptService().getConcept(conceptId));
        result.setObs(obs);
        encounterFulfillingOrders.setEditorResults(Collections.singletonList(result));
        pihAppsService.saveEncounterFulfillingOrders(encounterFulfillingOrders);
        Context.flushSession();
        Context.clearSession();
        return order(order.getUuid());
    }

    /**
     * @return a new panel obs group as an editor would give it: concepts and values only
     */
    private Obs panelGroup(Double weight, Double cd4) {
        Obs group = new Obs();
        group.setConcept(Context.getConceptService().getConcept(PANEL));
        for (Object[] member : new Object[][] { { WEIGHT, weight }, { CD4_COUNT, cd4 } }) {
            Obs obs = new Obs();
            obs.setConcept(Context.getConceptService().getConcept((Integer) member[0]));
            obs.setValueNumeric((Double) member[1]);
            group.addGroupMember(obs);
        }
        return group;
    }

    /**
     * @return the order's non-voided panel obs group, or null
     */
    private Obs groupResult(Order order) {
        return pihAppsService.getLabResultObs(order).stream()
                .filter(o -> o.getConcept().getConceptId() == PANEL).findFirst().orElse(null);
    }

    private List<OrderFulfillmentEventType> eventTypes(Order order) {
        return pihAppsService.getOrderFulfillmentEvents(order).stream().map(OrderFulfillmentEvent::getEventType).collect(Collectors.toList());
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

    /**
     * Validates the orders' results as they are now, as a validator who reviewed them would
     */
    private void validate(List<Order> orders, String comment) {
        Map<Order, List<String>> reviewed = new HashMap<>();
        for (Order order : orders) {
            reviewed.put(order, pihAppsService.getLabResultObs(order).stream().map(Obs::getUuid).collect(Collectors.toList()));
        }
        pihAppsService.validateLabResults(orders, reviewed, comment);
    }

    private List<String> orderUuids(OrderFulfillmentStatus status) {
        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));
        criteria.setOrderFulfillmentStatuses(Collections.singletonList(status));
        return pihAppsService.getOrders(criteria).getOrders().stream().map(Order::getUuid).collect(Collectors.toList());
    }

    private void saveEvent(Order order, OrderFulfillmentEventType type) {
        OrderFulfillmentEvent event = new OrderFulfillmentEvent();
        event.setOrder(order);
        event.setEventType(type);
        pihAppsService.saveOrderFulfillmentEvent(event);
        Context.flushSession();
    }

    private Order order(String uuid) {
        return Context.getOrderService().getOrderByUuid(uuid);
    }

    private boolean hasAwaitingValidationOption() {
        return labOrderConfig.getOrderFulfillmentStatusOptions().stream()
                .anyMatch(o -> OrderFulfillmentStatus.AWAITING_VALIDATION.name().equals(o.get("status")));
    }

    private Obs saveResult(Order order, Obs.Status status) {
        Concept weight = Context.getConceptService().getConcept(WEIGHT);
        Obs obs = new Obs();
        obs.setPerson(Context.getPersonService().getPerson(2));
        obs.setConcept(weight);
        obs.setObsDatetime(new Date());
        obs.setValueNumeric(60.0);
        obs.setOrder(order);
        obs.setStatus(status);
        return Context.getObsService().saveObs(obs, null);
    }

    private List<Integer> obsIds(ObsSearchCriteria criteria) {
        return pihAppsService.getObs(criteria).getObs().stream().map(Obs::getObsId).collect(Collectors.toList());
    }

    /**
     * @return a standard dataset user who did not enter any results, given the validate privilege through a role
     */
    private User validator() {
        UserService userService = Context.getUserService();
        User user = userService.getUser(VALIDATOR_USER);
        Role role = userService.getRole("Lab Result Validator");
        if (role == null) {
            role = new Role("Lab Result Validator", "Validates lab results");
            for (String p : Arrays.asList(LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS,
                    PrivilegeConstants.GET_ORDERS, PrivilegeConstants.EDIT_ORDERS, PrivilegeConstants.GET_OBS,
                    PrivilegeConstants.GET_ENCOUNTERS, PrivilegeConstants.GET_CONCEPTS, PrivilegeConstants.GET_USERS,
                    PrivilegeConstants.GET_PATIENTS)) {
                Privilege privilege = userService.getPrivilege(p);
                role.addPrivilege(privilege == null ? userService.savePrivilege(new Privilege(p, p)) : privilege);
            }
            role = userService.saveRole(role);
            user.addRole(role);
            userService.saveUser(user);
            Context.flushSession();
        }
        return user;
    }

    private User becomeValidator() {
        User user = validator();
        Context.becomeUser(user.getSystemId());
        return Context.getAuthenticatedUser();
    }

    private void assertAwaitingWithPreliminaryResult(Obs result) {
        Context.flushSession();
        Context.clearSession();
        Order order = order(ORDER_7);
        assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
        assertThat(latestValidationEvent(order).getEventType(), equalTo(OrderFulfillmentEventType.RESULTS_SUBMITTED));
        assertThat(savedStatus(result), equalTo(Obs.Status.PRELIMINARY));
    }

    private Set<Integer> encounterObsIds(Encounter encounter) {
        return Context.getEncounterService().getEncounterByUuid(encounter.getUuid()).getAllObs(true).stream()
                .map(Obs::getObsId).collect(Collectors.toSet());
    }

    private void executeSql(String sql) {
        Context.flushSession();
        Context.getAdministrationService().executeSQL(sql, false);
        Context.clearSession();
    }

    private void setGlobalProperty(String property, String value) {
        Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(property, value));
    }
}
