package org.openmrs.module.pihapps.rest;

import org.junit.jupiter.api.Test;
import org.openmrs.Obs;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LabResultsRestControllerTest {

    LabResultsRestController controller = new LabResultsRestController(null, null, null, null, null);

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
}
