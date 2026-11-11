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
import org.openmrs.api.context.ServiceContext;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEvent;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEventType;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
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
    UserContext userContext;
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
        userContext = mock(UserContext.class);
        when(userContext.hasPrivilege(anyString())).thenReturn(true);
        Context.setUserContext(userContext);
        // No REST converters are registered, so dates are converted by ConversionUtil's default
        ServiceContext.getInstance().setApplicationContext(mock(ApplicationContext.class));
    }

    @AfterEach
    public void tearDown() {
        OrderWithFulfillerDetailsResource.clearLabResultValidationCache();
        ServiceContext.getInstance().setApplicationContext(null);
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
        assertThat(o.get("eventDatetime"), instanceOf(String.class));
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
        assertThat(((Map<?, ?>) ((List<?>) o.get("history")).get(0)).get("eventDatetime"), instanceOf(String.class));
    }

    @Test
    public void getShouldNotOfferValidationWithoutPrivilege() {
        when(userContext.hasPrivilege(LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS)).thenReturn(false);
        when(pihAppsService.getOrderFulfillmentEvents(order)).thenReturn(Collections.emptyList());
        when(pihAppsService.getLabResultEditors(order)).thenReturn(Collections.emptySet());
        when(pihAppsService.canValidateLabResults(any(), any())).thenReturn(true);

        SimpleObject ret = (SimpleObject) controller.getValidation(Collections.singletonList("order-1"));

        assertThat(((Map<String, Object>) ret.get("order-1")).get("canValidate"), is(false));
    }

    @Test
    public void getShouldReturn400ForUnknownOrder() throws Exception {
        ResponseEntity<?> response = handle(() -> controller.getValidation(Collections.singletonList("missing")));
        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(body(response).get("success"), is(false));
        assertThat(body(response).get("message"), equalTo("Order missing not found"));
        assertThat(body(response).keySet(), equalTo(new HashSet<>(Arrays.asList("success", "message"))));
    }

    @Test
    public void getShouldReturn403WhenPrivilegeMissing() throws Exception {
        when(userContext.hasPrivilege(anyString())).thenReturn(false);
        ResponseEntity<?> response = handle(() -> controller.getValidation(Collections.singletonList("order-1")));
        assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(body(response).get("success"), is(false));
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
    public void postShouldReturn400ForApiExceptionBadActionAndUnknownOrder() throws Exception {
        doThrow(new APIException("reason required")).when(pihAppsService).returnLabResultsForCorrection(any(), any());
        ResponseEntity<?> response = postDecision("order-1", "RETURN");
        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(body(response).get("success"), is(false));
        assertThat(body(response).get("message"), equalTo("reason required"));

        assertThat(postDecision("order-1", "OTHER").getStatusCode(), is(HttpStatus.BAD_REQUEST));

        response = postDecision("missing", "VALIDATE");
        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat((String) body(response).get("message"), containsString("missing"));
        verify(pihAppsService, never()).validateLabResults(any(), any());
    }

    @Test
    public void postShouldReturn403WhenPrivilegeMissing() throws Exception {
        doThrow(new org.openmrs.api.APIAuthenticationException("no")).when(pihAppsService).validateLabResults(any(), any());
        ResponseEntity<?> response = postDecision("order-1", "VALIDATE");
        assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(body(response).get("success"), is(false));
    }

    private Map<?, ?> body(ResponseEntity<?> response) {
        return (Map<?, ?>) response.getBody();
    }

    private ResponseEntity<?> postDecision(String orderUuid, String action) throws Exception {
        LabResultValidationRestController.ValidationRequest request = new LabResultValidationRestController.ValidationRequest();
        request.setOrders(Collections.singletonList(orderUuid));
        request.setAction(action);
        return handle(() -> controller.decide(request));
    }

    /**
     * Calls the controller, and if it throws, answers with the exception handler Spring would pick for the exception
     */
    private ResponseEntity<?> handle(Callable<?> call) throws Exception {
        try {
            Object ret = call.call();
            return ret instanceof ResponseEntity ? (ResponseEntity<?>) ret : ResponseEntity.ok(ret);
        }
        catch (Exception e) {
            Method handler = new ExceptionHandlerMethodResolver(LabResultValidationRestController.class).resolveMethod(e);
            if (handler == null) {
                throw e;
            }
            return (ResponseEntity<?>) handler.invoke(controller, e);
        }
    }
}
