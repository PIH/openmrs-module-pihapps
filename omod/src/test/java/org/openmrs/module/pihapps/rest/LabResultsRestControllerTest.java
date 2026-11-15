package org.openmrs.module.pihapps.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.webservices.rest.web.api.RestService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class LabResultsRestControllerTest {

    LabResultsRestController controller;
    Concept category;
    Concept panel;
    Concept culture;
    Concept susceptibility;
    Concept isolate;
    Concept organism;
    Concept antibiotic;
    Concept otherTest;

    @BeforeEach
    public void setup() {
        controller = new LabResultsRestController(mock(PihAppsService.class), mock(LabOrderConfig.class),
                mock(OrderService.class), mock(ConceptService.class), mock(RestService.class));
        organism = concept(3);
        antibiotic = concept(4);
        isolate = concept(5, organism, antibiotic);
        susceptibility = concept(6, isolate);
        culture = concept(7);
        panel = concept(8, culture, susceptibility);
        otherTest = concept(9);
        category = concept(10, panel, otherTest);
    }

    @Test
    public void shouldReplaceTheMembersOfARenderedGroupByTheGroup() {
        List<Concept> concepts = controller.getSetMembersRecursively(category);
        assertThat(concepts, containsInAnyOrder(culture, organism, antibiotic, otherTest));
        List<Concept> collapsed = controller.collapseRenderedConcepts(concepts, Collections.singletonList(susceptibility));
        assertThat(collapsed, containsInAnyOrder(culture, susceptibility, otherTest));
    }

    @Test
    public void shouldNotAddARenderedGroupWhoseMembersAreNotSearched() {
        List<Concept> collapsed = controller.collapseRenderedConcepts(Arrays.asList(culture, otherTest), Collections.singletonList(susceptibility));
        assertThat(collapsed, containsInAnyOrder(culture, otherTest));
    }

    @Test
    public void shouldSearchAllConceptsWhenNoConceptsAreGiven() {
        assertThat(controller.collapseRenderedConcepts(null, Collections.singletonList(susceptibility)), nullValue());
    }

    @Test
    public void getStatuses_shouldMatchAnyStatusIfNoneAreNamed() {
        assertThat(controller.getStatuses(null), nullValue());
        assertThat(controller.getStatuses(Collections.emptyList()), nullValue());
    }

    @Test
    public void getStatuses_shouldParseNamedStatuses() {
        assertThat(controller.getStatuses(Arrays.asList("FINAL", " amended ")), contains(Obs.Status.FINAL, Obs.Status.AMENDED));
    }

    @Test
    public void getStatuses_shouldRejectUnknownStatus() {
        assertThrows(IllegalArgumentException.class, () -> controller.getStatuses(Collections.singletonList("VALIDATED")));
    }

    private Concept concept(int id, Concept... setMembers) {
        Concept concept = new Concept(id);
        for (Concept member : setMembers) {
            concept.addSetMember(member);
        }
        return concept;
    }
}
