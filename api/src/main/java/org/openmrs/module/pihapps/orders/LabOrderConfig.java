package org.openmrs.module.pihapps.orders;

import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.TestOrder;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.util.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LabOrderConfig {

    private static final Logger log = LoggerFactory.getLogger(LabOrderConfig.class);

    @Setter
    ConceptService conceptService;

    @Setter
    OrderService orderService;

    @Setter
    EncounterService encounterService;

    @Setter
    MessageSourceService messageSourceService;

    public List<Map<String, String>> getOrderStatusOptions() {
        List<Map<String, String>> l = new ArrayList<>();
        l.add(map("status", "", "display", messageSourceService.getMessage("pihapps.all")));
        for (OrderStatus s : OrderStatus.values()) {
            String display = messageSourceService.getMessage("pihapps.orderStatus." + s.name());
            l.add(map("status", s.name(), "display", display));
        }
        return l;
    }

    public List<Map<String, String>> getFulfillerStatusOptions() {
        List<Map<String, String>> l = new ArrayList<>();
        l.add(map("status", "", "display", messageSourceService.getMessage("pihapps.all")));
        l.add(map("status", "none", "display", messageSourceService.getMessage("pihapps.none")));
        for (Order.FulfillerStatus s : Order.FulfillerStatus.values()) {
            String display = messageSourceService.getMessage("pihapps.fulfillerStatus." + s.name());
            l.add(map("status", s.name(), "display", display));
        }
        return l;
    }

    public List<Map<String, String>> getOrderFulfillmentStatusOptions() {
        List<Map<String, String>> l = new ArrayList<>();
        l.add(map("status", "", "display", messageSourceService.getMessage("pihapps.all")));
        for (OrderFulfillmentStatus s : OrderFulfillmentStatus.values()) {
            String display = messageSourceService.getMessage("pihapps.orderFulfillmentStatus." + s.name());
            l.add(map("status", s.name(), "display", display));
        }
        return l;
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

    public List<OrderType> getTestOrderTypes() {
        List<OrderType> orderTypes = new ArrayList<>();
        for (OrderType orderType : orderService.getOrderTypes(false)) {
            try {
                Class<?> clazz = Context.loadClass(orderType.getJavaClassName());
                if (TestOrder.class.isAssignableFrom(clazz)) {
                    orderTypes.add(orderType);
                }
            }
            catch (ClassNotFoundException e) {
                log.warn("Unable to load order type class {}", orderType.getJavaClassName());
            }
        }
        return orderTypes;
    }

    // Lab Order Encounter Type

    public String getLabOrderEncounterTypeReference() {
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.labOrderEncounterType");
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("orderentryowa.encounterType");
        }
        return configVal;
    }

    public EncounterType getLabOrderEncounterType() {
        return getEncounterType(getLabOrderEncounterTypeReference());
    }

    // Lab Order Encounter Role

    public String getLabOrderEncounterRoleReference() {
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.labOrderEncounterRole");
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("orderentryowa.encounterRole");
        }
        return configVal;
    }

    public EncounterRole getLabOrderEncounterRole() {
        String encounterRoleRef = getLabOrderEncounterRoleReference();
        EncounterRole encounterRole = null;
        if (StringUtils.isNotBlank(encounterRoleRef)) {
            encounterRole = encounterService.getEncounterRoleByUuid(encounterRoleRef);
            if (encounterRole == null) {
                encounterRole = encounterService.getEncounterRoleByName(encounterRoleRef);
            }
        }
        if (encounterRole == null) {
            log.warn("Invalid labOrderEncounterRole configuration: " + encounterRoleRef);
        }
        return encounterRole;
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

    // Auto-expire days for lab orders

    public String getLabOrderAutoExpireDaysReference() {
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.autoExpireTimeInDays");
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("orderentryowa.labOrderAutoExpireTimeInDays");
        }
        return configVal;
    }

    public int getLabOrderAutoExpireDays() {
        int autoExpireDays = 30;
        String configVal = getLabOrderAutoExpireDaysReference();
        if (StringUtils.isNotBlank(configVal)) {
            try {
                autoExpireDays = Integer.parseInt(configVal);
            } catch (NumberFormatException e) {
                log.warn("Invalid autoExpireTimeInDaysConfiguration, integer expected: " + configVal);
            }
        }
        return autoExpireDays;
    }

    // Lab Concept Display Name

    public String getConceptDisplayFormat() {
        return ConfigUtil.getGlobalProperty("pihapps.labs.conceptDisplayFormat");
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

    public List<LabTestCategory> getAvailableLabTestsByCategory() {
        List<LabTestCategory> ret = new ArrayList<>();
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
                    LabTestCategory c = new LabTestCategory();
                    c.setCategory(category);
                    c.setLabTests(conceptsInCategory);
                    ret.add(c);
                }
            }
        }
        return ret;
    }

    public String getSpecimenCollectionEncounterTypeReference() {
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.specimenCollectionEncounterType");
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("labworkflowowa.labResultsEntryEncounterType");
        }
        return configVal;
    }

    public EncounterType getSpecimenCollectionEncounterType() {
        return getEncounterType(getSpecimenCollectionEncounterTypeReference());
    }

    public String getSpecimenCollectionEncounterRoleReference() {
        return ConfigUtil.getGlobalProperty("pihapps.labs.specimenCollectionEncounterRole");
    }

    public EncounterRole getSpecimenCollectionEncounterRole() {
        String encounterRoleRef = getSpecimenCollectionEncounterRoleReference();
        EncounterRole encounterRole = null;
        if (StringUtils.isNotBlank(encounterRoleRef)) {
            encounterRole = encounterService.getEncounterRoleByUuid(encounterRoleRef);
            if (encounterRole == null) {
                encounterRole = encounterService.getEncounterRoleByName(encounterRoleRef);
            }
        }
        return encounterRole;
    }

    public String getEstimatedCollectionDateQuestionReference() {
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.estimatedCollectionDateQuestion");
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("labworkflowowa.estimatedCollectionDateQuestion");
        }
        return configVal;
    }

    public Concept getEstimatedCollectionDateQuestion() {
        return conceptService.getConceptByReference(getEstimatedCollectionDateQuestionReference());
    }

    public String getEstimatedCollectionDateAnswerReference() {
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.estimatedCollectionDateAnswer");
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("labworkflowowa.estimatedCollectionDateAnswer");
        }
        return configVal;
    }

    public Concept getEstimatedCollectionDateAnswer() {
        return conceptService.getConceptByReference(getEstimatedCollectionDateAnswerReference());
    }

    public String getTestOrderNumberQuestionReference() {
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.testOrderNumberConcept");
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("labworkflowowa.testOrderNumberConcept");
        }
        return configVal;
    }

    public Concept getTestOrderNumberQuestion() {
        return conceptService.getConceptByReference(getTestOrderNumberQuestionReference());
    }

    public String getLabIdentifierConceptReference() {
        return ConfigUtil.getProperty("pihapps.labs.labIdentifierConcept", "CIEL:162086");
    }

    public Concept getLabIdentifierConcept() {
        return conceptService.getConceptByReference(getLabIdentifierConceptReference());
    }

    public String getTestLocationQuestionReference() {
        String configVal = ConfigUtil.getGlobalProperty("pihapps.labs.locationOfLaboratory");
        if (StringUtils.isBlank(configVal)) {
            configVal = ConfigUtil.getGlobalProperty("labworkflowowa.locationOfLaboratory");
        }
        return configVal;
    }

    public Concept getTestLocationQuestion() {
        return conceptService.getConceptByReference(getTestLocationQuestionReference());
    }

    public String getSpecimenReceivedDateQuestionReference() {
        return ConfigUtil.getProperty("pihapps.labs.specimenReceivedDateConcept", "PIH:21057");
    }

    public Concept getSpecimenReceivedDateQuestion() {
        return conceptService.getConceptByReference(getSpecimenReceivedDateQuestionReference());
    }

    private Map<String, String> map(String... keysAndValues) {
        Map<String, String> ret = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            ret.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return ret;
    }

    private EncounterType getEncounterType(String encounterTypeRef) {
        EncounterType encounterType = null;
        if (StringUtils.isNotBlank(encounterTypeRef)) {
            encounterType = encounterService.getEncounterTypeByUuid(encounterTypeRef);
            if (encounterType == null) {
                encounterType = encounterService.getEncounterType(encounterTypeRef);
            }
        }
        if (encounterType == null) {
            log.warn("Invalid configuration.  No matching encounter type for reference: " + encounterTypeRef);
        }
        return encounterType;
    }
}
