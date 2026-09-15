package org.openmrs.module.pihapps.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.Concept;
import org.openmrs.ConceptReferenceRange;
import org.openmrs.ConceptReferenceRangeContext;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.webservices.rest.SimpleObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LabResultsRestControllerTest {

    ConceptService conceptService;
    LabResultsRestController controller;
    Obs obs;

    @BeforeEach
    public void setup() {
        conceptService = mock(ConceptService.class);
        controller = new LabResultsRestController(mock(PihAppsService.class), mock(LabOrderConfig.class),
                mock(OrderService.class), conceptService);

        obs = new Obs();
        obs.setConcept(new Concept());
        Patient patient = new Patient();
        obs.setPerson(patient);
        obs.setObsDatetime(new Date());
    }

    private SimpleObject resultsWrapping(Map<String, Object> obsMap) {
        List<Object> results = new ArrayList<>();
        results.add(obsMap);
        SimpleObject simpleResult = new SimpleObject();
        simpleResult.put("results", results);
        return simpleResult;
    }

    @Test
    public void populateEffectiveReferenceRanges_shouldFillInFallbackWhenReferenceRangeMissing() {
        Map<String, Object> obsMap = new HashMap<>();
        obsMap.put("referenceRange", null);
        SimpleObject simpleResult = resultsWrapping(obsMap);

        ConceptReferenceRange fallbackRange = new ConceptReferenceRange();
        fallbackRange.setLowNormal(4.0);
        fallbackRange.setHiNormal(10.0);
        when(conceptService.getConceptReferenceRange(any(ConceptReferenceRangeContext.class))).thenReturn(fallbackRange);

        controller.populateEffectiveReferenceRanges(Collections.singletonList(obs), simpleResult);

        Map<?, ?> referenceRange = (Map<?, ?>) obsMap.get("referenceRange");
        assertThat(referenceRange.get("lowNormal"), equalTo(4.0));
        assertThat(referenceRange.get("hiNormal"), equalTo(10.0));

        ArgumentCaptor<ConceptReferenceRangeContext> captor = ArgumentCaptor.forClass(ConceptReferenceRangeContext.class);
        verify(conceptService).getConceptReferenceRange(captor.capture());
        assertThat(captor.getValue().getPerson(), equalTo(obs.getPerson()));
        assertThat(captor.getValue().getDate(), equalTo(obs.getObsDatetime()));
    }

    @Test
    public void populateEffectiveReferenceRanges_shouldNotOverwriteWhenReferenceRangeAlreadyPresent() {
        Map<String, Object> existingRange = new HashMap<>();
        existingRange.put("lowNormal", 1.0);
        Map<String, Object> obsMap = new HashMap<>();
        obsMap.put("referenceRange", existingRange);
        SimpleObject simpleResult = resultsWrapping(obsMap);

        controller.populateEffectiveReferenceRanges(Collections.singletonList(obs), simpleResult);

        assertThat(obsMap.get("referenceRange"), is(existingRange));
        verify(conceptService, never()).getConceptReferenceRange(any(ConceptReferenceRangeContext.class));
    }

    @Test
    public void populateEffectiveReferenceRanges_shouldNotAddReferenceRangeWhenPropertyNotRequested() {
        Map<String, Object> obsMap = new HashMap<>();
        SimpleObject simpleResult = resultsWrapping(obsMap);

        controller.populateEffectiveReferenceRanges(Collections.singletonList(obs), simpleResult);

        assertThat(obsMap.containsKey("referenceRange"), is(false));
        verify(conceptService, never()).getConceptReferenceRange(any(ConceptReferenceRangeContext.class));
    }

    @Test
    public void populateEffectiveReferenceRanges_shouldLeaveReferenceRangeNullWhenNoFallbackAvailable() {
        Map<String, Object> obsMap = new HashMap<>();
        obsMap.put("referenceRange", null);
        SimpleObject simpleResult = resultsWrapping(obsMap);

        when(conceptService.getConceptReferenceRange(any(ConceptReferenceRangeContext.class))).thenReturn(null);

        controller.populateEffectiveReferenceRanges(Collections.singletonList(obs), simpleResult);

        assertThat(obsMap.get("referenceRange"), is(nullValue()));
    }

    @Test
    public void toSimpleObject_shouldMapAllRangeFields() {
        ConceptReferenceRange range = new ConceptReferenceRange();
        range.setLowNormal(1.0);
        range.setHiNormal(2.0);
        range.setLowAbsolute(3.0);
        range.setHiAbsolute(4.0);
        range.setLowCritical(5.0);
        range.setHiCritical(6.0);

        SimpleObject result = controller.toSimpleObject(range);

        assertThat(result.get("lowNormal"), equalTo(1.0));
        assertThat(result.get("hiNormal"), equalTo(2.0));
        assertThat(result.get("lowAbsolute"), equalTo(3.0));
        assertThat(result.get("hiAbsolute"), equalTo(4.0));
        assertThat(result.get("lowCritical"), equalTo(5.0));
        assertThat(result.get("hiCritical"), equalTo(6.0));
    }
}
