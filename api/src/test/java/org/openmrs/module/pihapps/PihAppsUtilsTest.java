package org.openmrs.module.pihapps;

import org.junit.jupiter.api.Test;
import org.openmrs.Concept;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PihAppsUtilsTest {

    @Test
    public void getConceptHierarchy_shouldReturnJustRoot_whenRootHasNoMembers() {
        Concept root = conceptWithId(1, "root");
        when(root.getSetMembers()).thenReturn(Collections.emptyList());

        Set<String> result = uuids(PihAppsUtils.getConceptHierarchy(root));

        assertEquals(Collections.singleton("root"), result);
    }

    @Test
    public void getConceptHierarchy_shouldCollectAllUuids_inTree() {
        /*
         * root
         * |- child1
         *   |- grandChild1
         *   |- grandChild2
         * |- child2
         */
        Concept root       = conceptWithId(1, "root");
        Concept child1     = conceptWithId(2, "child1");
        Concept child2     = conceptWithId(3, "child2");
        Concept grandChild1 = conceptWithId(4, "grandChild1");
        Concept grandChild2 = conceptWithId(5, "grandChild2");

        when(root.getSetMembers()).thenReturn(Arrays.asList(child1, child2));
        when(child1.getSetMembers()).thenReturn(Arrays.asList(grandChild1, grandChild2));
        when(child2.getSetMembers()).thenReturn(Collections.emptyList());
        when(grandChild1.getSetMembers()).thenReturn(Collections.emptyList());
        when(grandChild2.getSetMembers()).thenReturn(Collections.emptyList());

        Set<String> result = uuids(PihAppsUtils.getConceptHierarchy(root));

        assertEquals(new HashSet<>(Arrays.asList("root", "child1", "child2", "grandChild1", "grandChild2")), result);
    }

    @Test
    public void getConceptHierarchy_shouldHandleDiamondGraph_withoutDuplicates() {
        /*
         * root
         * |- child1 --|
         * |- child2 --|--> shared
         */
        Concept root   = conceptWithId(1, "root");
        Concept child1 = conceptWithId(2, "child1");
        Concept child2 = conceptWithId(3, "child2");
        Concept shared = conceptWithId(4, "shared");

        when(root.getSetMembers()).thenReturn(Arrays.asList(child1, child2));
        when(child1.getSetMembers()).thenReturn(Collections.singletonList(shared));
        when(child2.getSetMembers()).thenReturn(Collections.singletonList(shared));
        when(shared.getSetMembers()).thenReturn(Collections.emptyList());

        Set<String> result = uuids(PihAppsUtils.getConceptHierarchy(root));

        assertEquals(new HashSet<>(Arrays.asList("root", "child1", "child2", "shared")), result);
        assertEquals(4, result.size());
    }

    @Test
    public void getConceptHierarchy_shouldHandleCycles_withoutInfiniteLoop() {
        // Cycle: A -> B -> A
        Concept a = conceptWithId(1, "A");
        Concept b = conceptWithId(2, "B");

        when(a.getSetMembers()).thenReturn(Collections.singletonList(b));
        when(b.getSetMembers()).thenReturn(Collections.singletonList(a));

        Set<String> result = uuids(PihAppsUtils.getConceptHierarchy(a));

        assertEquals(new HashSet<>(Arrays.asList("A", "B")), result);
    }

    private static Concept conceptWithId(int id, String uuid) {
        Concept c = mock(Concept.class);
        when(c.getConceptId()).thenReturn(id);
        when(c.getUuid()).thenReturn(uuid);
        return c;
    }

    private static Set<String> uuids(Set<Concept> concepts) {
        return concepts.stream().map(Concept::getUuid).collect(Collectors.toSet());
    }
}
