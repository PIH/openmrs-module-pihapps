package org.openmrs.module.pihapps.rest;

import org.apache.commons.lang.NotImplementedException;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        description.addProperty("editorResults");
        return description;
    }

    /**
     * Results recorded as obs groups: a list of {order, concept, obs}, where obs is an obs group in the representation
     * of the obs resource (concept, groupMembers, values), or null to remove the order's result of the concept
     */
    @PropertySetter("editorResults")
    public static void setEditorResults(EncounterFulfillingOrders delegate, Object value) {
        List<EncounterFulfillingOrders.EditorResult> results = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Each editor result must be an object with order, concept and obs");
                }
                Map<?, ?> map = (Map<?, ?>) item;
                EncounterFulfillingOrders.EditorResult result = new EncounterFulfillingOrders.EditorResult();
                result.setOrder(map.get("order") == null ? null : (Order) ConversionUtil.convert(map.get("order"), Order.class));
                result.setConcept(map.get("concept") == null ? null : (Concept) ConversionUtil.convert(map.get("concept"), Concept.class));
                result.setObs(map.get("obs") == null ? null : (Obs) ConversionUtil.convert(map.get("obs"), Obs.class));
                results.add(result);
            }
        }
        delegate.setEditorResults(results);
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation || rep instanceof RefRepresentation) {
            DelegatingResourceDescription description = new DelegatingResourceDescription();
            description.addProperty("encounter", rep);
            description.addProperty("orders", rep);
            return description;
        }
        return null;
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
