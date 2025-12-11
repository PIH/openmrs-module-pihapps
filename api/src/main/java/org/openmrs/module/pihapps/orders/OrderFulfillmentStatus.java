package org.openmrs.module.pihapps.orders;

import lombok.Getter;
import org.openmrs.Order;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.openmrs.Order.FulfillerStatus.COMPLETED;
import static org.openmrs.Order.FulfillerStatus.DECLINED;
import static org.openmrs.Order.FulfillerStatus.EXCEPTION;
import static org.openmrs.Order.FulfillerStatus.IN_PROGRESS;
import static org.openmrs.Order.FulfillerStatus.ON_HOLD;
import static org.openmrs.Order.FulfillerStatus.RECEIVED;

public enum OrderFulfillmentStatus {

    AWAITING_FULFILLMENT(OrderStatus.ACTIVE, Collections.singletonList(RECEIVED), Boolean.TRUE),
    IN_FULFILLMENT(null, Arrays.asList(IN_PROGRESS, ON_HOLD), null),
    COMPLETED_FULFILLMENT(null, Collections.singletonList(COMPLETED), null),
    UNABLE_TO_COMPLETE_FULFILLMENT(null, Arrays.asList(EXCEPTION, DECLINED), null),
    EXPIRED_BEFORE_FULFILLMENT(OrderStatus.EXPIRED, Collections.singletonList(RECEIVED), Boolean.TRUE),
    CANCELLED_BEFORE_FULFILLMENT(OrderStatus.STOPPED, Collections.singletonList(RECEIVED), Boolean.TRUE);

    @Getter
    private final OrderStatus orderStatus;

    @Getter
    private final List<Order.FulfillerStatus> fulfillerStatuses;

    @Getter
    private final Boolean includeNullFulfillerStatus;

    OrderFulfillmentStatus(OrderStatus orderStatus,  List<Order.FulfillerStatus> fulfillerStatuses, Boolean includeNullFulfillerStatus) {
        this.orderStatus = orderStatus;
        this.fulfillerStatuses = fulfillerStatuses;
        this.includeNullFulfillerStatus = includeNullFulfillerStatus;
    }
}
