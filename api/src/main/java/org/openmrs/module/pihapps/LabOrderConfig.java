package org.openmrs.module.pihapps;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LabOrderConfig {

    private static final Log log = LogFactory.getLog(LabOrderConfig.class);

    final ConceptService conceptService;
    final OrderService orderService;

    public LabOrderConfig(@Autowired ConceptService conceptService, @Autowired OrderService orderService) {
        this.conceptService = conceptService;
        this.orderService = orderService;
    }

    public String getLabOrderablesConceptSetReference() {
        return ConfigUtil.getGlobalProperty("orderentryowa.labOrderablesConceptSet");
    }

    public String getOrderReasonsReferenceConfig() {
        return ConfigUtil.getGlobalProperty("orderentryowa.orderReasonsMap");
    }

    public Concept getLabOrderablesConceptSet() {
        return conceptService.getConceptByReference(getLabOrderablesConceptSetReference());
    }

    /**
     * @return the default care setting for lab orders, defined as the first found care setting of type OUTPATIENT
     */
    public CareSetting getDefaultCareSetting() {
        List<CareSetting> careSettings = orderService.getCareSettings(false);
        for (CareSetting cs : careSettings) {
            if (cs.getCareSettingType() == CareSetting.CareSettingType.OUTPATIENT) {
                return cs;
            }
        }
        return careSettings.isEmpty() ? null : careSettings.get(0);
    }

    /**
     * TODO: Eventually this will allow restricting based on global property, etc
     */
    public Map<Concept, List<Concept>> getAvailableLabTestsByCategory() {
        Map<Concept, List<Concept>> ret = new LinkedHashMap<>();
        Concept labOrderablesConceptSet = getLabOrderablesConceptSet();
        if (labOrderablesConceptSet != null) {
            for (Concept category : labOrderablesConceptSet.getSetMembers()) {
                ret.put(category, category.getSetMembers());
            }
        }
        return ret;
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
}
