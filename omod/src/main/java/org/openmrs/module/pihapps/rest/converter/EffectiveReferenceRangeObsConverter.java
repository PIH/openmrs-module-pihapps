package org.openmrs.module.pihapps.rest.converter;

import org.openmrs.BaseReferenceRange;
import org.openmrs.ConceptReferenceRangeContext;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.Converter;
import org.openmrs.module.webservices.rest.web.response.ConversionException;

/**
 * Wraps the standard Obs converter to add an "effectiveReferenceRange" property: the reference range directly
 * associated with the obs if there is one, otherwise the reference range associated with its concept (evaluated
 * as of the obs's own date, so that date-relative criteria like age-at-encounter are evaluated correctly for
 * historical results, not as of today). This is distinct from "referenceRange", which continues to reflect only
 * a direct obs-level association, so that a client can still tell the two apart.
 */
public class EffectiveReferenceRangeObsConverter implements Converter<Obs> {

    private final Converter<Obs> delegate;

    private final ConceptService conceptService;

    public EffectiveReferenceRangeObsConverter(Converter<Obs> delegate, ConceptService conceptService) {
        this.delegate = delegate;
        this.conceptService = conceptService;
    }

    @Override
    public Obs newInstance(String type) {
        return delegate.newInstance(type);
    }

    @Override
    public Obs getByUniqueId(String string) {
        return delegate.getByUniqueId(string);
    }

    @Override
    public SimpleObject asRepresentation(Obs instance, Representation rep) throws ConversionException {
        SimpleObject representation = delegate.asRepresentation(instance, rep);
        if (representation.containsKey("referenceRange")) {
            BaseReferenceRange effectiveRange = instance.getReferenceRange();
            if (effectiveRange == null) {
                effectiveRange = conceptService.getConceptReferenceRange(new ConceptReferenceRangeContext(instance));
            }
            representation.put("effectiveReferenceRange", effectiveRange == null ? null : toSimpleObject(effectiveRange));
        }
        return representation;
    }

    @Override
    public Object getProperty(Obs instance, String propertyName) throws ConversionException {
        return delegate.getProperty(instance, propertyName);
    }

    @Override
    public void setProperty(Object instance, String propertyName, Object value) throws ConversionException {
        delegate.setProperty(instance, propertyName, value);
    }

    static SimpleObject toSimpleObject(BaseReferenceRange range) {
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
