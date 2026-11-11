package org.openmrs.module.pihapps.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEvent;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEventType;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class LabResultValidationRestTest {

    PihAppsService pihAppsService;
    OrderService orderService;
    LabResultValidationRestController controller;
    Order order;

    @BeforeEach
    public void setup() {
        pihAppsService = mock(PihAppsService.class);
        orderService = mock(OrderService.class);
        controller = new LabResultValidationRestController();
        controller.pihAppsService = pihAppsService;
        controller.orderService = orderService;
        order = new TestOrder();
        order.setUuid("order-1");
        when(orderService.getOrderByUuid("order-1")).thenReturn(order);
        UserContext userContext = mock(UserContext.class);
        when(userContext.hasPrivilege(anyString())).thenReturn(true);
        Context.setUserContext(userContext);
    }

    @AfterEach
    public void tearDown() {
        OrderWithFulfillerDetailsResource.clearLabResultValidationCache();
        Context.clearUserContext();
    }

    private OrderFulfillmentEvent event(OrderFulfillmentEventType type, String comment) {
        OrderFulfillmentEvent e = new OrderFulfillmentEvent();
        e.setOrder(order);
        e.setEventType(type);
        e.setComment(comment);
        e.setEventDatetime(new Date());
        return e;
    }

    @Test
    public void resourcePropertyShouldUseTheCacheWithoutQueryingTheService() {
        OrderFulfillmentEvent e = event(OrderFulfillmentEventType.RESULTS_RETURNED, "recheck");
        Map<Order, OrderFulfillmentEvent> cache = new HashMap<>();
        cache.put(order, e);
        OrderWithFulfillerDetailsResource.primeLabResultValidationCache(cache);

        SimpleObject o = new OrderWithFulfillerDetailsResource().getLabResultValidation(order);

        assertThat(o.get("eventType"), is("RESULTS_RETURNED"));
        assertThat(o.get("comment"), is("recheck"));
        assertThat(o.get("eventDatetime"), is(e.getEventDatetime()));
        assertThat(o.containsKey("creator"), is(true));
        assertThat(o.get("creator"), nullValue());
        verifyNoInteractions(pihAppsService);
    }

    @Test
    public void resourcePropertyShouldBeNullForOrderMissingFromPrimedCache() {
        OrderWithFulfillerDetailsResource.primeLabResultValidationCache(Collections.emptyMap());
        assertThat(new OrderWithFulfillerDetailsResource().getLabResultValidation(order), nullValue());
    }

    @Test
    public void getShouldReturnLatestEventHistoryAndCanValidate() {
        OrderFulfillmentEvent submitted = event(OrderFulfillmentEventType.RESULTS_SUBMITTED, null);
        OrderFulfillmentEvent returned = event(OrderFulfillmentEventType.RESULTS_RETURNED, "recheck");
        when(pihAppsService.getOrderFulfillmentEvents(order)).thenReturn(Arrays.asList(submitted, returned));
        when(pihAppsService.getLabResultEditors(order)).thenReturn(Collections.emptySet());
        when(pihAppsService.canValidateLabResults(any(), any())).thenReturn(true);

        SimpleObject ret = (SimpleObject) controller.getValidation(Collections.singletonList("order-1"));

        Map<String, Object> o = (Map<String, Object>) ret.get("order-1");
        assertThat(o.get("eventType"), is("RESULTS_RETURNED"));
        assertThat(o.get("comment"), is("recheck"));
        assertThat(o.get("enteredByCurrentUser"), is(false));
        assertThat(o.get("canValidate"), is(true));
        assertThat(((List<?>) o.get("history")).size(), is(2));
    }

    @Test
    public void getShouldReturnNullEventTypeWhenNoEvents() {
        when(pihAppsService.getOrderFulfillmentEvents(order)).thenReturn(Collections.emptyList());
        when(pihAppsService.getLabResultEditors(order)).thenReturn(Collections.emptySet());

        SimpleObject ret = (SimpleObject) controller.getValidation(Collections.singletonList("order-1"));

        Map<String, Object> o = (Map<String, Object>) ret.get("order-1");
        assertThat(o.get("eventType"), nullValue());
        assertThat(((List<?>) o.get("history")).isEmpty(), is(true));
    }

    @Test
    public void postShouldValidateAndReturn() {
        LabResultValidationRestController.ValidationRequest request = new LabResultValidationRestController.ValidationRequest();
        request.setOrders(Collections.singletonList("order-1"));
        request.setAction("VALIDATE");
        request.setComment("ok");
        ResponseEntity<?> response = controller.decide(request);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        verify(pihAppsService).validateLabResults(Collections.singletonList(order), "ok");

        request.setAction("RETURN");
        request.setComment("bad");
        controller.decide(request);
        verify(pihAppsService).returnLabResultsForCorrection(Collections.singletonList(order), "bad");
    }

    @Test
    public void postShouldReturn400ForApiExceptionBadActionAndUnknownOrder() {
        LabResultValidationRestController.ValidationRequest request = new LabResultValidationRestController.ValidationRequest();
        request.setOrders(Collections.singletonList("order-1"));
        request.setAction("RETURN");
        doThrow(new APIException("reason required")).when(pihAppsService).returnLabResultsForCorrection(any(), any());
        ResponseEntity<?> response = controller.decide(request);
        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(((Map<?, ?>) response.getBody()).get("success"), is(false));
        assertThat(((Map<?, ?>) response.getBody()).get("message"), equalTo("reason required"));

        request.setAction("OTHER");
        assertThat(controller.decide(request).getStatusCode(), is(HttpStatus.BAD_REQUEST));

        request.setAction("VALIDATE");
        request.setOrders(Collections.singletonList("missing"));
        assertThat(controller.decide(request).getStatusCode(), is(HttpStatus.BAD_REQUEST));
        verify(pihAppsService, never()).validateLabResults(any(), any());
    }

    @Test
    public void postShouldReturn403WhenPrivilegeMissing() {
        LabResultValidationRestController.ValidationRequest request = new LabResultValidationRestController.ValidationRequest();
        request.setOrders(Collections.singletonList("order-1"));
        request.setAction("VALIDATE");
        doThrow(new org.openmrs.api.APIAuthenticationException("no")).when(pihAppsService).validateLabResults(any(), any());
        assertThat(controller.decide(request).getStatusCode(), is(HttpStatus.FORBIDDEN));
    }
}
