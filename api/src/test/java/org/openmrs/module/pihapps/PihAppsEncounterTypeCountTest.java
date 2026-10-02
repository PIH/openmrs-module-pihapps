package org.openmrs.module.pihapps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/**
 * Covers counting encounters by type. The audit fixture adds encounters of types 1 and 2 on top of
 * those in core's standard dataset, a voided one among them, so a count that got voided encounters
 * wrong either way, or missed the fixture's, would not match core's own search.
 */
public class PihAppsEncounterTypeCountTest extends BaseModuleContextSensitiveTest {

    private PihAppsService service;

    private EncounterService encounterService;

    @BeforeEach
    public void setup() {
        executeDataSet("encounterAuditTestDataset.xml");
        service = Context.getService(PihAppsService.class);
        encounterService = Context.getEncounterService();
    }

    /** What core's own search says, as an independent check on the grouped query. */
    private long encounterCount(EncounterType encounterType, boolean includeVoided) {
        return encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
                .setEncounterTypes(Collections.singletonList(encounterType))
                .setIncludeVoided(includeVoided)
                .createEncounterSearchCriteria()).size();
    }

    @Test
    public void shouldCountNonVoidedEncountersOfEachType() {
        Map<EncounterType, Long> counts = service.getEncounterTypeCounts(false, false);

        for (Map.Entry<EncounterType, Long> entry : counts.entrySet()) {
            assertThat(entry.getKey().getName(), entry.getValue(), is(encounterCount(entry.getKey(), false)));
        }
    }

    @Test
    public void shouldCountEveryEncounterOfEachTypeWhenAskedToIncludeVoided() {
        Map<EncounterType, Long> counts = service.getEncounterTypeCounts(true, false);

        for (Map.Entry<EncounterType, Long> entry : counts.entrySet()) {
            assertThat(entry.getKey().getName(), entry.getValue(), is(encounterCount(entry.getKey(), true)));
        }
    }

    @Test
    public void shouldLeaveVoidedEncountersOutOfTheCountUnlessAsked() {
        EncounterType typeOne = encounterService.getEncounterType(1);
        long withVoided = encounterCount(typeOne, true);

        // the fixture voids encounter 3003, of type 1
        assertThat(service.getEncounterTypeCounts(false, false).get(typeOne), is(withVoided - 1));
        assertThat(service.getEncounterTypeCounts(true, false).get(typeOne), is(withVoided));
    }

    @Test
    public void shouldCountATypeWhoseEncountersHaveAllBeenVoidedOnlyWhenAsked() {
        EncounterType deletedOnly = new EncounterType("Deleted only", "Every encounter of this type is voided");
        encounterService.saveEncounterType(deletedOnly);
        Encounter encounter = encounterService.getEncounter(3003);
        encounter.setEncounterType(deletedOnly);
        encounterService.saveEncounter(encounter);

        assertThat(service.getEncounterTypeCounts(false, false).get(deletedOnly), is(0L));
        assertThat(service.getEncounterTypeCounts(true, false).get(deletedOnly), is(1L));
    }

    @Test
    public void shouldListEveryTypeIncludingRetiredAndUnusedOnes() {
        Map<EncounterType, Long> counts = service.getEncounterTypeCounts(true, false);

        assertThat(new ArrayList<>(counts.keySet()),
                containsInAnyOrder(encounterService.getAllEncounterTypes(true).toArray()));
    }

    @Test
    public void shouldCountZeroForATypeWithNoEncounters() {
        EncounterType unused = new EncounterType("Unused type", "Nothing is recorded against this");
        encounterService.saveEncounterType(unused);

        assertThat(service.getEncounterTypeCounts(true, false).get(unused), is(0L));
    }

    @Test
    public void shouldLeaveOutTypesWithNoEncountersWhenAskedForOnlyUsedOnes() {
        EncounterType unused = new EncounterType("Unused type", "Nothing is recorded against this");
        encounterService.saveEncounterType(unused);

        Map<EncounterType, Long> counts = service.getEncounterTypeCounts(true, true);

        assertThat(counts.containsKey(unused), is(false));
        assertThat(counts.values().stream().allMatch(count -> count > 0), is(true));
        // and every type that is used is still there, with the same count
        Map<EncounterType, Long> allCounts = service.getEncounterTypeCounts(true, false);
        allCounts.values().removeIf(count -> count == 0);
        assertThat(counts, is(allCounts));
    }

    @Test
    public void shouldDecideWhichTypesAreUsedByWhichEncountersAreCounted() {
        EncounterType deletedOnly = new EncounterType("Deleted only", "Every encounter of this type is voided");
        encounterService.saveEncounterType(deletedOnly);
        Encounter encounter = encounterService.getEncounter(3003);
        encounter.setEncounterType(deletedOnly);
        encounterService.saveEncounter(encounter);

        assertThat(service.getEncounterTypeCounts(false, true).containsKey(deletedOnly), is(false));
        assertThat(service.getEncounterTypeCounts(true, true).get(deletedOnly), is(1L));
    }

    @Test
    public void shouldOrderTypesByName() {
        List<String> names = service.getEncounterTypeCounts(true, false).keySet().stream()
                .map(EncounterType::getName)
                .collect(Collectors.toList());
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);

        assertThat(names, is(sorted));
    }
}
