package org.openmrs.module.pihapps.orders;

import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class LabOrderConfigTest extends BaseModuleContextSensitiveTest {

    @Autowired
    LabOrderConfig labOrderConfig;

    @Test
    public void getMultipleAnswerConceptsReference_shouldReturnPihAppsPropertyWhenSet() {
        Context.getAdministrationService().setGlobalProperty(
            "pihapps.labs.multipleAnswerConcepts", "CIEL:123");
        assertThat(labOrderConfig.getMultipleAnswerConceptsReference(), equalTo("CIEL:123"));
    }

    @Test
    public void getMultipleAnswerConceptsReference_shouldFallBackToLegacyProperty() {
        Context.getAdministrationService().setGlobalProperty(
            "pihapps.labs.multipleAnswerConcepts", "");
        Context.getAdministrationService().setGlobalProperty(
            "laboratorymanagement.multipleAnswerConceptIds", "CIEL:456");
        assertThat(labOrderConfig.getMultipleAnswerConceptsReference(), equalTo("CIEL:456"));
    }

    @Test
    public void getMultipleAnswerConceptsReference_shouldReturnBlankWhenNeitherSet() {
        Context.getAdministrationService().setGlobalProperty(
            "pihapps.labs.multipleAnswerConcepts", "");
        Context.getAdministrationService().setGlobalProperty(
            "laboratorymanagement.multipleAnswerConceptIds", "");
        assertThat(labOrderConfig.getMultipleAnswerConceptsReference(), blankOrNullString());
    }

    @Test
    public void getMultipleAnswerConcepts_shouldReturnEmptyListWhenNotConfigured() {
        Context.getAdministrationService().setGlobalProperty(
            "pihapps.labs.multipleAnswerConcepts", "");
        Context.getAdministrationService().setGlobalProperty(
            "laboratorymanagement.multipleAnswerConceptIds", "");
        List<Concept> result = labOrderConfig.getMultipleAnswerConcepts();
        assertThat(result, empty());
    }
}
