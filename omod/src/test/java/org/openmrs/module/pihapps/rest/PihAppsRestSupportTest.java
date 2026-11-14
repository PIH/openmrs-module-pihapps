package org.openmrs.module.pihapps.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PihAppsRestSupportTest {

    @Test
    public void hasMoreResults_shouldBeTrueOnlyWhenRowsRemainAfterThePage() {
        assertFalse(PihAppsRestSupport.hasMoreResults(0, 100, 100L));
        assertTrue(PihAppsRestSupport.hasMoreResults(0, 100, 101L));
        assertTrue(PihAppsRestSupport.hasMoreResults(100, 100, 201L));
        assertFalse(PihAppsRestSupport.hasMoreResults(100, 100, 200L));
        assertFalse(PihAppsRestSupport.hasMoreResults(null, 100, 50L));
    }

    @Test
    public void hasMoreResults_shouldBeFalseWithoutALimit() {
        assertFalse(PihAppsRestSupport.hasMoreResults(0, null, 500L));
    }
}
