package org.openmrs.module.pihapps.rest;

import org.openmrs.BaseReferenceRange;
import org.openmrs.ConceptReferenceRangeContext;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs2_7.ObsResource2_7;

@Resource(name = RestConstants.VERSION_1 + "/obs", supportedClass = Obs.class, supportedOpenmrsVersions = "*", order = 0)
public class ExtendedObsResource extends ObsResource2_7 {

    /**
     * The reference range directly associated with the obs if there is one, otherwise the reference range
     * associated with its concept (evaluated as of the obs's own date, so that date-relative criteria like
     * age-at-encounter are evaluated correctly for historical results, not as of today). This is distinct from
     * "referenceRange", which continues to reflect only a direct obs-level association, so that a client can
     * still tell the two apart.
     */
    @PropertyGetter("effectiveReferenceRange")
    public SimpleObject getEffectiveReferenceRange(Obs obs) {
        BaseReferenceRange effectiveRange = obs.getReferenceRange();
        if (effectiveRange == null) {
            effectiveRange = Context.getConceptService().getConceptReferenceRange(new ConceptReferenceRangeContext(obs));
        }
        return effectiveRange == null ? null : toSimpleObject(effectiveRange);
    }

    private SimpleObject toSimpleObject(BaseReferenceRange range) {
        SimpleObject rangeObject = new SimpleObject();
        rangeObject.add("hiNormal", range.getHiNormal());
        rangeObject.add("hiAbsolute", range.getHiAbsolute());
        rangeObject.add("hiCritical", range.getHiCritical());
        rangeObject.add("lowNormal", range.getLowNormal());
        rangeObject.add("lowAbsolute", range.getLowAbsolute());
        rangeObject.add("lowCritical", range.getLowCritical());
        return rangeObject;
    }
}
