package org.openmrs.module.pihapps.rest;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs2_2.OrderResource2_2;

import java.util.Map;

@Resource(name = RestConstants.VERSION_1 + "/order", supportedClass = Order.class, supportedOpenmrsVersions = "*", order = 0)
public class OrderWithFulfillerDetailsResource extends OrderResource2_2 {

    // Populated by LabOrderRestController before serializing a page of orders, so fulfillerEncounter can be
    // resolved via one batch query instead of one query per order. Falls back to the per-order lookup below
    // for any request that doesn't go through that controller (e.g. a direct GET on a single order).
    private static final ThreadLocal<Map<Order, Encounter>> FULFILLER_ENCOUNTER_CACHE = new ThreadLocal<>();

    public static void primeFulfillerEncounterCache(Map<Order, Encounter> fulfillerEncountersByOrder) {
        FULFILLER_ENCOUNTER_CACHE.set(fulfillerEncountersByOrder);
    }

    public static void clearFulfillerEncounterCache() {
        FULFILLER_ENCOUNTER_CACHE.remove();
    }

    @PropertyGetter("fulfillerEncounter")
    public Encounter getFulfillerEncounter(Order order) {
        Map<Order, Encounter> cache = FULFILLER_ENCOUNTER_CACHE.get();
        if (cache != null) {
            return cache.get(order);
        }
        return Context.getService(PihAppsService.class).getFulfillerEncounterForOrder(order);
    }

    @PropertyGetter("reasonOrderNotFulfilled")
    public Obs getReasonOrderNotFulfilled(Order order) {
        return Context.getService(PihAppsService.class).getReasonOrderNotFulfilled(order);
    }
}
