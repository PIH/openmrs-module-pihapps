package org.openmrs.module.pihapps.orders;

import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

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

    @Test
    public void getFieldDependencyRule_shouldReturnNullWhenNotConfigured() {
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.testFieldDependencies", "");
        assertThat(labOrderConfig.getFieldDependencyRule("1305AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"), nullValue());
    }

    @Test
    public void getFieldDependencyRule_shouldReturnNullForConceptNotConfiguredAsTrigger() {
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.testFieldDependencies",
            "[{\"triggerConcept\":\"1305AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"answers\":{}}]");
        assertThat(labOrderConfig.getFieldDependencyRule("some-other-concept-uuid"), nullValue());
    }

    @Test
    public void getFieldDependencyRule_shouldReturnAnswersMapForConfiguredTriggerConcept() {
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.testFieldDependencies",
            "[{\"triggerConcept\":\"1305AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"answers\":{"
                + "\"1301AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\":{\"show\":[\"3cd4a882-26fe-102b-80cb-0017a47871b2\"]},"
                + "\"1302AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\":{\"show\":[\"53cb83ed-5d55-4b63-922f-d6b8fc67a5f8\"],\"defaults\":{\"53cb83ed-5d55-4b63-922f-d6b8fc67a5f8\":839}}"
                + "}}]");

        Map<String, Object> rule = labOrderConfig.getFieldDependencyRule("1305AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        assertThat(rule, notNullValue());
        assertThat(rule.keySet(), containsInAnyOrder(
            "1301AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "1302AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getFieldDependencyRule_shouldParseShowAndDefaultsForAnswer() {
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.testFieldDependencies",
            "[{\"triggerConcept\":\"1305AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"answers\":{"
                + "\"1301AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\":{\"show\":[\"3cd4a882-26fe-102b-80cb-0017a47871b2\"]},"
                + "\"1302AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\":{\"show\":[\"53cb83ed-5d55-4b63-922f-d6b8fc67a5f8\"],\"defaults\":{\"53cb83ed-5d55-4b63-922f-d6b8fc67a5f8\":839}}"
                + "}}]");

        Map<String, Object> rule = labOrderConfig.getFieldDependencyRule("1305AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        Map<String, Object> notDetectedRule = (Map<String, Object>) rule.get("1302AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        assertThat((List<String>) notDetectedRule.get("show"), contains("53cb83ed-5d55-4b63-922f-d6b8fc67a5f8"));
        assertThat((Map<String, Object>) notDetectedRule.get("defaults"), hasEntry("53cb83ed-5d55-4b63-922f-d6b8fc67a5f8", 839));
    }

    @Test
    public void getFieldDependencyRule_shouldReturnNullOnInvalidJson() {
        Context.getAdministrationService().setGlobalProperty("pihapps.labs.testFieldDependencies", "not valid json");
        assertThat(labOrderConfig.getFieldDependencyRule("1305AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"), nullValue());
    }
}
