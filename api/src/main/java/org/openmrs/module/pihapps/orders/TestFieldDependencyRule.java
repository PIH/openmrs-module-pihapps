package org.openmrs.module.pihapps.orders;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Conditional show/hide/default rule between tests within a panel, configured via the
 * pihapps.labs.testFieldDependencies global property as a JSON array of these, e.g.:
 * [{"triggerConcept":"<uuid>","answers":{"<answerUuid>":{"show":["<uuid>"],"defaults":{"<uuid>":839}}}}]
 */
@Data
public class TestFieldDependencyRule {

    private String triggerConcept;
    private Map<String, AnswerRule> answers;

    @Data
    public static class AnswerRule {
        private List<String> show;
        private Map<String, Object> defaults;
    }
}
