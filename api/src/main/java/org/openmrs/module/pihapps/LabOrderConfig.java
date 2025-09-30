package org.openmrs.module.pihapps;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptName;
import org.openmrs.OrderType;
import org.openmrs.api.ConceptNameType;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LabOrderConfig {

    private static final Log log = LogFactory.getLog(LabOrderConfig.class);

    final ConceptService conceptService;
    final OrderService orderService;

    @Autowired
    public LabOrderConfig(ConceptService conceptService, OrderService orderService) {
        this.conceptService = conceptService;
        this.orderService = orderService;
    }

    // Lab Orderables Concept Set

    public String getLabOrderablesConceptSetReference() {
        return ConfigUtil.getGlobalProperty("orderentryowa.labOrderablesConceptSet");
    }

    public Concept getLabOrderablesConceptSet() {
        return conceptService.getConceptByReference(getLabOrderablesConceptSetReference());
    }

    // Lab Order Reasons by Concept

    public String getOrderReasonsReferenceConfig() {
        return ConfigUtil.getGlobalProperty("orderentryowa.orderReasonsMap");
    }

    public Map<Concept, List<Concept>> getOrderReasonsMap() {
        Map<Concept, List<Concept>> orderReasonsMap = new HashMap<>();
        String orderReasonProp = getOrderReasonsReferenceConfig();
        if (StringUtils.isNotBlank(orderReasonProp)) {
            for (String conceptToReasonSet : orderReasonProp.split(",")) {
                String[] split = conceptToReasonSet.split("=");
                if (split.length != 2) {
                    log.warn("Invalid orderReasonMap entry: " + conceptToReasonSet);
                }
                else {
                    Concept orderableConcept = conceptService.getConceptByReference(split[0]);
                    if (orderableConcept == null) {
                        log.warn("Invalid orderReasonMap entry.  Unable to find concept " + split[0]);
                    }
                    Concept reasonConcept = conceptService.getConceptByReference(split[1]);
                    if (reasonConcept == null) {
                        log.warn("Invalid orderReasonMap entry.  Unable to find concept " + split[1]);
                    }
                    if (orderableConcept != null && reasonConcept != null) {
                        List<Concept> orderReasons = new ArrayList<>();
                        if (reasonConcept.getSetMembers() != null && !reasonConcept.getSetMembers().isEmpty()) {
                            orderReasons.addAll(reasonConcept.getSetMembers());
                        }
                        else if (reasonConcept.getAnswers() != null && !reasonConcept.getAnswers().isEmpty()) {
                            for (ConceptAnswer ca : reasonConcept.getAnswers()) {
                                orderReasons.add(ca.getAnswerConcept());
                            }
                        }
                        else {
                            log.warn("Invalid orderReasonMap entry: " + split[1] + " has no set members or answers");
                        }
                        orderReasonsMap.put(orderableConcept, orderReasons);
                    }
                }
            }
        }
        return orderReasonsMap;
    }

    // Lab Order Type

    public String getLabTestOrderTypeReference() {
        // First get value from this module
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.labOrderType");
        // If not set, attempt configuration from labworkflow owa
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("labworkflowowa.testOrderType");
        }
        // If not set, attempt configuration from rwanda laboratorymanagement module
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("laboratorymanagement.orderType.labOrderTypeId");
        }
        // If still not set, use the default value from core
        if (StringUtils.isBlank(configVal)) {
            configVal = OrderType.TEST_ORDER_TYPE_UUID;
        }
        return configVal;
    }

    public OrderType getLabTestOrderType() {
        String orderTypeRef = getLabTestOrderTypeReference();
        OrderType orderType = orderService.getOrderTypeByUuid(orderTypeRef);
        if (orderType == null) {
            orderType = orderService.getOrderType(Integer.parseInt(orderTypeRef));
        }
        return orderType;
    }

    // Lab Care Setting

    public CareSetting getDefaultCareSetting() {
        List<CareSetting> careSettings = orderService.getCareSettings(false);
        for (CareSetting cs : careSettings) {
            if (cs.getCareSettingType() == CareSetting.CareSettingType.OUTPATIENT) {
                return cs;
            }
        }
        return careSettings.isEmpty() ? null : careSettings.get(0);
    }

    // Lab Concept Display Name

    public String formatConcept(Concept c) {
        String format = ConfigUtil.getGlobalProperty("pihapps.labs.conceptDisplayFormat");
        if ("shortest".equals(format)) {
            return getBestShortName(c);
        }
        return c.getDisplayString();
    }

    /**
     * @return the best short name for a concept
     * Taken from orderentryowa - helpers.getConceptShortName
     */
    public String getBestShortName(Concept c) {
        ConceptName preferredShortLocale = null;
        ConceptName shortLocale = null;
        ConceptName preferredLocale = null;
        ConceptName preferredShortEnglish = null;
        ConceptName shortEnglish = null;
        if (c == null || c.getNames() == null || c.getNames().isEmpty()) {
            return "";
        }
        // Get the locale for the current locale, language only
        Locale locale = Context.getLocale();
        String language = locale.getLanguage();
        for (ConceptName cn : c.getNames()) {
            boolean isShort = cn.getConceptNameType() == ConceptNameType.SHORT;
            boolean isPreferred = cn.isPreferred();
            boolean isLocale = cn.getLocale().equals(locale) || cn.getLocale().getLanguage().equals(language);
            boolean isEnglish = cn.getLocale().getLanguage().equals("en");
            if (isPreferred && isShort && isLocale) {
                preferredShortLocale = cn;
            }
            else if (isShort && isLocale) {
                shortLocale = cn;
            }
            else if (isPreferred && isLocale) {
                preferredLocale = cn;
            }
            else if (isPreferred && isShort && isEnglish) {
                preferredShortEnglish = cn;
            }
            else if (isShort && isEnglish) {
                shortEnglish = cn;
            }
        }
        if (preferredShortLocale != null) {
            return preferredShortLocale.getName();
        }
        if (shortLocale != null) {
            return shortLocale.getName();
        }
        if (preferredLocale != null) {
            return preferredLocale.getName();
        }
        if (preferredShortEnglish != null) {
            return preferredShortEnglish.getName();
        }
        if (shortEnglish != null) {
            return shortEnglish.getName();
        }
        return c.getDisplayString();
    }

    // Enabled Lab Tests By Category

    public String getEnabledTestsConfig() {
        // Support legacy laboratorymanagement configuration for Rwanda
        return ConfigUtil.getGlobalProperty("laboratorymanagement.currentLabRequestFormConceptIDs");
    }

    public List<Concept> getEnabledLabTests() {
        List<Concept> ret = new ArrayList<>();
        String configVal = getEnabledTestsConfig();
        if (StringUtils.isNotBlank(configVal)) {
            for (String lookup : configVal.split(",")) {
                lookup = lookup.trim();
                Concept concept = conceptService.getConceptByReference(lookup);
                if (concept == null) {
                    log.warn("Invalid concept configured for enabled lab tests: " + lookup);
                }
                else {
                    ret.add(concept);
                }
            }
        }
        return ret;
    }

    public Map<Concept, List<Concept>> getAvailableLabTestsByCategory() {
        Map<Concept, List<Concept>> ret = new LinkedHashMap<>();
        Concept labOrderablesConceptSet = getLabOrderablesConceptSet();
        if (labOrderablesConceptSet != null) {
            List<Concept> enabledLabTests = getEnabledLabTests();
            for (Concept category : labOrderablesConceptSet.getSetMembers()) {
                List<Concept> conceptsInCategory = new ArrayList<>();
                for (Concept labTest : category.getSetMembers()) {
                    if (enabledLabTests.isEmpty() || enabledLabTests.contains(labTest)) {
                        conceptsInCategory.add(labTest);
                    }
                }
                if (!conceptsInCategory.isEmpty()) {
                    ret.put(category, conceptsInCategory);
                }
            }
        }
        return ret;
    }
}
