package org.openmrs.module.pihapps.rest.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.Concept;
import org.openmrs.ConceptReferenceRange;
import org.openmrs.ConceptReferenceRangeContext;
import org.openmrs.Obs;
import org.openmrs.ObsReferenceRange;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.Converter;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EffectiveReferenceRangeObsConverterTest {

    ConceptService conceptService;
    Converter<Obs> delegate;
    EffectiveReferenceRangeObsConverter converter;
    Obs obs;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {
        conceptService = mock(ConceptService.class);
        delegate = mock(Converter.class);
        converter = new EffectiveReferenceRangeObsConverter(delegate, conceptService);

        obs = new Obs();
        obs.setConcept(new Concept());
        Patient patient = new Patient();
        obs.setPerson(patient);
        obs.setObsDatetime(new Date());
    }

    @Test
    public void asRepresentation_shouldMirrorDirectReferenceRangeWithoutCallingConceptService() {
        ObsReferenceRange directRange = new ObsReferenceRange();
        directRange.setLowNormal(2.0);
        directRange.setHiNormal(8.0);
        obs.setReferenceRange(directRange);

        SimpleObject delegateResult = new SimpleObject();
        delegateResult.put("referenceRange", new SimpleObject()); // representation already reflects the direct association
        when(delegate.asRepresentation(obs, Representation.DEFAULT)).thenReturn(delegateResult);

        SimpleObject result = converter.asRepresentation(obs, Representation.DEFAULT);

        SimpleObject effectiveRange = (SimpleObject) result.get("effectiveReferenceRange");
        assertThat(effectiveRange.get("lowNormal"), equalTo(2.0));
        assertThat(effectiveRange.get("hiNormal"), equalTo(8.0));
        verify(conceptService, never()).getConceptReferenceRange(any(ConceptReferenceRangeContext.class));
    }

    @Test
    public void asRepresentation_shouldFallBackToConceptLevelRangeWhenObsHasNone() {
        SimpleObject delegateResult = new SimpleObject();
        delegateResult.put("referenceRange", null);
        when(delegate.asRepresentation(obs, Representation.DEFAULT)).thenReturn(delegateResult);

        ConceptReferenceRange fallbackRange = new ConceptReferenceRange();
        fallbackRange.setLowNormal(4.0);
        fallbackRange.setHiNormal(10.0);
        when(conceptService.getConceptReferenceRange(any(ConceptReferenceRangeContext.class))).thenReturn(fallbackRange);

        SimpleObject result = converter.asRepresentation(obs, Representation.DEFAULT);

        // the raw, obs-associated property is untouched
        assertThat(result.get("referenceRange"), is(nullValue()));

        SimpleObject effectiveRange = (SimpleObject) result.get("effectiveReferenceRange");
        assertThat(effectiveRange.get("lowNormal"), equalTo(4.0));
        assertThat(effectiveRange.get("hiNormal"), equalTo(10.0));

        ArgumentCaptor<ConceptReferenceRangeContext> captor = ArgumentCaptor.forClass(ConceptReferenceRangeContext.class);
        verify(conceptService).getConceptReferenceRange(captor.capture());
        assertThat(captor.getValue().getPerson(), equalTo(obs.getPerson()));
        assertThat(captor.getValue().getDate(), equalTo(obs.getObsDatetime()));
    }

    @Test
    public void asRepresentation_shouldNotAddPropertyWhenReferenceRangeNotRequested() {
        SimpleObject delegateResult = new SimpleObject();
        when(delegate.asRepresentation(obs, Representation.DEFAULT)).thenReturn(delegateResult);

        SimpleObject result = converter.asRepresentation(obs, Representation.DEFAULT);

        assertThat(result.containsKey("effectiveReferenceRange"), is(false));
        verify(conceptService, never()).getConceptReferenceRange(any(ConceptReferenceRangeContext.class));
    }

    @Test
    public void asRepresentation_shouldSetEffectiveReferenceRangeNullWhenNoFallbackAvailable() {
        SimpleObject delegateResult = new SimpleObject();
        delegateResult.put("referenceRange", null);
        when(delegate.asRepresentation(obs, Representation.DEFAULT)).thenReturn(delegateResult);
        when(conceptService.getConceptReferenceRange(any(ConceptReferenceRangeContext.class))).thenReturn(null);

        SimpleObject result = converter.asRepresentation(obs, Representation.DEFAULT);

        assertThat(result.containsKey("effectiveReferenceRange"), is(true));
        assertThat(result.get("effectiveReferenceRange"), is(nullValue()));
    }
}
