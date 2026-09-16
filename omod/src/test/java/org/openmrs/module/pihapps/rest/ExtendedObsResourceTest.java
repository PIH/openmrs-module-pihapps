package org.openmrs.module.pihapps.rest;

import org.junit.jupiter.api.AfterEach;
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
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.webservices.rest.SimpleObject;

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

public class ExtendedObsResourceTest {

    ConceptService conceptService;
    ExtendedObsResource resource;
    Obs obs;

    @BeforeEach
    public void setup() {
        // Touching Context before ServiceContext avoids a log4j/ServiceContext circular-init NPE that occurs
        // when ServiceContext.getInstance() is the very first OpenMRS class touched in the test JVM (see
        // GenerateLabIdRestControllerTest, which establishes user context first for the same reason).
        Context.setUserContext(mock(UserContext.class));

        conceptService = mock(ConceptService.class);
        ServiceContext.getInstance().setConceptService(conceptService);

        resource = new ExtendedObsResource();

        obs = new Obs();
        obs.setConcept(new Concept());
        Patient patient = new Patient();
        obs.setPerson(patient);
        obs.setObsDatetime(new Date());
    }

    @AfterEach
    public void tearDown() {
        ServiceContext.getInstance().setConceptService(null);
        Context.clearUserContext();
    }

    @Test
    public void getEffectiveReferenceRange_shouldMirrorDirectReferenceRangeWithoutCallingConceptService() {
        ObsReferenceRange directRange = new ObsReferenceRange();
        directRange.setLowNormal(2.0);
        directRange.setHiNormal(8.0);
        obs.setReferenceRange(directRange);

        SimpleObject effectiveRange = resource.getEffectiveReferenceRange(obs);

        assertThat(effectiveRange.get("lowNormal"), equalTo(2.0));
        assertThat(effectiveRange.get("hiNormal"), equalTo(8.0));
        verify(conceptService, never()).getConceptReferenceRange(any(ConceptReferenceRangeContext.class));
    }

    @Test
    public void getEffectiveReferenceRange_shouldFallBackToConceptLevelRangeWhenObsHasNone() {
        ConceptReferenceRange fallbackRange = new ConceptReferenceRange();
        fallbackRange.setLowNormal(4.0);
        fallbackRange.setHiNormal(10.0);
        when(conceptService.getConceptReferenceRange(any(ConceptReferenceRangeContext.class))).thenReturn(fallbackRange);

        SimpleObject effectiveRange = resource.getEffectiveReferenceRange(obs);

        assertThat(effectiveRange.get("lowNormal"), equalTo(4.0));
        assertThat(effectiveRange.get("hiNormal"), equalTo(10.0));

        ArgumentCaptor<ConceptReferenceRangeContext> captor = ArgumentCaptor.forClass(ConceptReferenceRangeContext.class);
        verify(conceptService).getConceptReferenceRange(captor.capture());
        assertThat(captor.getValue().getPerson(), equalTo(obs.getPerson()));
        assertThat(captor.getValue().getDate(), equalTo(obs.getObsDatetime()));
    }

    @Test
    public void getEffectiveReferenceRange_shouldReturnNullWhenNoFallbackAvailable() {
        when(conceptService.getConceptReferenceRange(any(ConceptReferenceRangeContext.class))).thenReturn(null);

        SimpleObject effectiveRange = resource.getEffectiveReferenceRange(obs);

        assertThat(effectiveRange, is(nullValue()));
    }
}
