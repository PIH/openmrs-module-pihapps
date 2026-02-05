package org.openmrs.module.pihapps.rest;

import org.apache.commons.lang.NotImplementedException;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

@Resource(name = RestConstants.VERSION_1 + "/encounterFulfillingOrders", supportedClass = EncounterFulfillingOrders.class, supportedOpenmrsVersions = "*")
public class EncounterFulfillingOrdersResource extends DelegatingCrudResource<EncounterFulfillingOrders> {

    @Override
    public EncounterFulfillingOrders getByUniqueId(String encounterUuid) {
        return Context.getService(PihAppsService.class).getEncounterFulfillingOrders(encounterUuid);
    }

    @Override
    protected void delete(EncounterFulfillingOrders encounterFulfillingOrders, String s, RequestContext requestContext) throws ResponseException {
        throw new NotImplementedException();  // TODO
    }

    @Override
    public void purge(EncounterFulfillingOrders encounterFulfillingOrders, RequestContext requestContext) throws ResponseException {
        throw new NotImplementedException();  // TODO
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("encounter");
        description.addProperty("orders");
        return description;
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
        return getCreatableProperties();
    }

    @Override
    public EncounterFulfillingOrders newDelegate() {
        return new EncounterFulfillingOrders();
    }

    @Override
    public EncounterFulfillingOrders save(EncounterFulfillingOrders encounterFulfillingOrders) {
        return Context.getService(PihAppsService.class).saveEncounterFulfillingOrders(encounterFulfillingOrders);
    }
}
