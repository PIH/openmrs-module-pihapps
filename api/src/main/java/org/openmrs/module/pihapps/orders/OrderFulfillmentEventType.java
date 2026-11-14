package org.openmrs.module.pihapps.orders;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Types of {@link OrderFulfillmentEvent}.  These are stored by name, so a value must never be renamed or removed.
 */
public enum OrderFulfillmentEventType {

    RESULTS_SUBMITTED,
    RESULTS_RETURNED,
    RESULTS_VALIDATED,
    /**
     * Results submitted for validation were withdrawn: the order left in progress (not performed, reverted...) before
     * they were validated or returned
     */
    RESULTS_WITHDRAWN,
    /**
     * The results of an order were changed after they were validated.  Not part of {@link #RESULT_VALIDATION}: it does
     * not change whether the order awaits validation
     */
    RESULTS_AMENDED;

    /**
     * The event types that make up the result validation workflow: the latest of them tells whether an order's
     * results await validation
     */
    public static final Set<OrderFulfillmentEventType> RESULT_VALIDATION =
            Collections.unmodifiableSet(EnumSet.of(RESULTS_SUBMITTED, RESULTS_RETURNED, RESULTS_VALIDATED, RESULTS_WITHDRAWN));
}
