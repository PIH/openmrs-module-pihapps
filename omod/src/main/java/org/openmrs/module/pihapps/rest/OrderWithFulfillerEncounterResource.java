package org.openmrs.module.pihapps.rest;

import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs2_2.OrderResource2_2;

@Resource(name = RestConstants.VERSION_1 + "/order", supportedClass = Order.class, supportedOpenmrsVersions = "*", order = 0)
public class OrderWithFulfillerEncounterResource extends OrderResource2_2 {

    @PropertyGetter("fulfillerEncounter")
    public Encounter getFulfillerEncounter(Order order) {
        return Context.getService(PihAppsService.class).getFulfillerEncouterForOrder(order);
    }

}
