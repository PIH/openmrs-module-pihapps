package org.openmrs.module.pihapps;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEvent;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEventType;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.openmrs.module.pihapps.orders.OrderFulfillmentEventType.RESULTS_RETURNED;
import static org.openmrs.module.pihapps.orders.OrderFulfillmentEventType.RESULTS_SUBMITTED;
import static org.openmrs.module.pihapps.orders.OrderFulfillmentEventType.RESULT_VALIDATION;

public class OrderFulfillmentEventTest extends BaseModuleContextSensitiveTest {

    // Standard dataset order 7, and dataset orders 102 and 103
    private static final String ORDER_7 = "2c96f25c-4949-4f72-9931-d808fbc226df";
    private static final String ORDER_102 = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    private static final String ORDER_103 = "c3d4e5f6-a7b8-9012-cdef-123456789012";

    @Autowired
    @Qualifier("pihappsService")
    private PihAppsService pihAppsService;

    @BeforeEach
    public void setup() {
        executeDataSet("pihapps_lab_order_test_data.xml");
    }

    @Test
    public void saveOrderFulfillmentEvent_shouldPersistAndDefaultEventDatetime() {
        Order order = order(ORDER_7);
        OrderFulfillmentEvent event = new OrderFulfillmentEvent();
        event.setOrder(order);
        event.setEventType(RESULTS_SUBMITTED);
        event.setComment("x");
        pihAppsService.saveOrderFulfillmentEvent(event);
        Context.flushSession();
        Context.clearSession();

        OrderFulfillmentEvent saved = (OrderFulfillmentEvent) getSession()
                .createQuery("from OrderFulfillmentEvent where uuid = :uuid")
                .setParameter("uuid", event.getUuid()).uniqueResult();
        assertThat(saved.getOrder().getUuid(), equalTo(ORDER_7));
        assertThat(saved.getEventType(), equalTo(RESULTS_SUBMITTED));
        assertThat(saved.getComment(), equalTo("x"));
        assertThat(saved.getEventDatetime(), notNullValue());
        assertThat(saved.getCreator(), equalTo(Context.getAuthenticatedUser()));
    }

    @Test
    public void getLatestOrderFulfillmentEvents_shouldReturnLatestPerOrder() {
        save(ORDER_7, RESULTS_SUBMITTED);
        save(ORDER_7, RESULTS_RETURNED);
        save(ORDER_102, RESULTS_SUBMITTED);

        Map<Order, OrderFulfillmentEvent> latest = pihAppsService.getLatestOrderFulfillmentEvents(
                Arrays.asList(order(ORDER_7), order(ORDER_102), order(ORDER_103)), RESULT_VALIDATION);
        assertThat(latest.size(), equalTo(2));
        assertThat(latest.get(order(ORDER_7)).getEventType(), equalTo(RESULTS_RETURNED));
        assertThat(latest.get(order(ORDER_102)).getEventType(), equalTo(RESULTS_SUBMITTED));
        assertThat(latest, not(hasKey(order(ORDER_103))));
    }

    @Test
    public void getLatestOrderFulfillmentEvents_shouldIgnoreVoidedEvents() {
        save(ORDER_7, RESULTS_SUBMITTED);
        OrderFulfillmentEvent returned = save(ORDER_7, RESULTS_RETURNED);
        returned.setVoided(true);
        returned.setVoidedBy(Context.getAuthenticatedUser());
        returned.setDateVoided(new Date());
        returned.setVoidReason("test");
        pihAppsService.saveOrderFulfillmentEvent(returned);

        Map<Order, OrderFulfillmentEvent> latest = pihAppsService.getLatestOrderFulfillmentEvents(
                Arrays.asList(order(ORDER_7)), RESULT_VALIDATION);
        assertThat(latest.get(order(ORDER_7)).getEventType(), equalTo(RESULTS_SUBMITTED));
    }

    @Test
    public void getOrderFulfillmentEvents_shouldReturnNonVoidedOldestFirst() {
        OrderFulfillmentEvent submitted = save(ORDER_7, RESULTS_SUBMITTED);
        OrderFulfillmentEvent returned = save(ORDER_7, RESULTS_RETURNED);
        OrderFulfillmentEvent resubmitted = save(ORDER_7, RESULTS_SUBMITTED);
        save(ORDER_102, RESULTS_SUBMITTED);
        returned.setVoided(true);
        returned.setVoidedBy(Context.getAuthenticatedUser());
        returned.setDateVoided(new Date());
        returned.setVoidReason("test");
        pihAppsService.saveOrderFulfillmentEvent(returned);

        List<String> uuids = pihAppsService.getOrderFulfillmentEvents(order(ORDER_7)).stream()
                .map(OrderFulfillmentEvent::getUuid).collect(Collectors.toList());
        assertThat(uuids, contains(submitted.getUuid(), resubmitted.getUuid()));
    }

    private OrderFulfillmentEvent save(String orderUuid, OrderFulfillmentEventType type) {
        OrderFulfillmentEvent event = new OrderFulfillmentEvent();
        event.setOrder(order(orderUuid));
        event.setEventType(type);
        OrderFulfillmentEvent saved = pihAppsService.saveOrderFulfillmentEvent(event);
        Context.flushSession();
        return saved;
    }

    private Order order(String uuid) {
        return Context.getOrderService().getOrderByUuid(uuid);
    }

    private Session getSession() {
        return Context.getRegisteredComponent("sessionFactory", SessionFactory.class).getCurrentSession();
    }
}
