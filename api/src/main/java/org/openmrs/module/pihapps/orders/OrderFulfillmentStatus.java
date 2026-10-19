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

    AWAITING_FULFILLMENT(OrderStatus.ACTIVE, Collections.singletonList(RECEIVED), Boolean.TRUE, null),
    IN_FULFILLMENT(null, Arrays.asList(IN_PROGRESS, ON_HOLD), null, Boolean.FALSE),
    AWAITING_VALIDATION(null, Collections.singletonList(IN_PROGRESS), null, Boolean.TRUE),
    COMPLETED_FULFILLMENT(null, Collections.singletonList(COMPLETED), null, null),
    UNABLE_TO_COMPLETE_FULFILLMENT(null, Arrays.asList(EXCEPTION, DECLINED), null, null),
    EXPIRED_BEFORE_FULFILLMENT(OrderStatus.EXPIRED, Collections.singletonList(RECEIVED), Boolean.TRUE, null),
    CANCELLED_BEFORE_FULFILLMENT(OrderStatus.STOPPED, Collections.singletonList(RECEIVED), Boolean.TRUE, null);

    /**
     * The fulfiller comment that marks an order whose results have been entered and await validation,
     * when result validation is required.  The order stays IN_PROGRESS until a validator completes it.
     */
    public static final String RESULTS_AWAITING_VALIDATION = "Results awaiting validation";

    @Getter
    private final OrderStatus orderStatus;

    @Getter
    private final List<Order.FulfillerStatus> fulfillerStatuses;

    @Getter
    private final Boolean includeNullFulfillerStatus;

    /**
     * TRUE to match only orders awaiting validation, FALSE to exclude them, null to ignore validation
     */
    @Getter
    private final Boolean awaitingValidation;

    OrderFulfillmentStatus(OrderStatus orderStatus,  List<Order.FulfillerStatus> fulfillerStatuses, Boolean includeNullFulfillerStatus,
                           Boolean awaitingValidation) {
        this.orderStatus = orderStatus;
        this.fulfillerStatuses = fulfillerStatuses;
        this.includeNullFulfillerStatus = includeNullFulfillerStatus;
        this.awaitingValidation = awaitingValidation;
    }
}
