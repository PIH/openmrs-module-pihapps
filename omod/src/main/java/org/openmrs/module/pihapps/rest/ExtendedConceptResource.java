package org.openmrs.module.pihapps.rest;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.PihAppsUtils;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs2_0.ConceptResource2_0;

@Resource(name = RestConstants.VERSION_1 + "/concept", supportedClass = Concept.class, supportedOpenmrsVersions = "*", order = 0)
public class ExtendedConceptResource extends ConceptResource2_0 {

    @PropertyGetter("displayStringForLab")
    public String getDisplayStringForLab(Concept concept) {
        return Context.getRegisteredComponents(PihAppsUtils.class).get(0).getBestShortName(concept);
    }
}
