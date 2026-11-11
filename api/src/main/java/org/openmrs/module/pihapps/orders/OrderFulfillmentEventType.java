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
    RESULTS_VALIDATED;

    /**
     * The event types that make up the result validation workflow
     */
    public static final Set<OrderFulfillmentEventType> RESULT_VALIDATION =
            Collections.unmodifiableSet(EnumSet.of(RESULTS_SUBMITTED, RESULTS_RETURNED, RESULTS_VALIDATED));
}
