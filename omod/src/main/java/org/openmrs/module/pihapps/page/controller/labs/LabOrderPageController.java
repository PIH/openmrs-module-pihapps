package org.openmrs.module.pihapps.page.controller.labs;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.pihapps.PihAppsUtils;
import org.openmrs.module.pihapps.service.LabPrescriptionService;
import org.openmrs.module.pihapps.service.impl.LabPrescriptionServiceImpl;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.ConfigUtil;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

public class LabOrderPageController {

    private static final Log log = LogFactory.getLog(LabOrderPageController.class);

    public void get(PageModel model, UiUtils ui,
                      @InjectBeans PatientDomainWrapper patientDomainWrapper,
                      @RequestParam(value = "patient") Patient patient,
                      @RequestParam(value = "returnUrl", required = false) String returnUrl,
                      @SpringBean("conceptService") ConceptService conceptService,
                      @SpringBean LabPrescriptionService labPrescriptionService
                    ) {

        String labSetProp = ConfigUtil.getGlobalProperty("orderentryowa.labOrderablesConceptSet");
        Concept labSet = conceptService.getConceptByReference(labSetProp);

        String orderReasonProp = ConfigUtil.getGlobalProperty("orderentryowa.orderReasonsMap");
        Map<String, List<Concept>> orderReasonsMap = new HashMap<>();
        if (StringUtils.isNotBlank(orderReasonProp)) {
            for (String conceptUuidToReasonSet : orderReasonProp.split(",")) {
                String[] split = conceptUuidToReasonSet.split("=");
                if (split.length != 2) {
                    log.warn("Invalid orderReasonMap entry: " + conceptUuidToReasonSet);
                }
                else {
                    Concept reasonConcept = conceptService.getConceptByReference(split[1]);
                    if (reasonConcept == null) {
                        log.warn("Invalid orderReasonMap entry.  Unable to find concept " + split[1]);
                    }
                    else {
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
                        orderReasonsMap.put(split[0], orderReasons);
                    }
                }
            }
        }
        // Get lab prescriptions for this patient
        List<String> labPrescriptions = labPrescriptionService.getLabPrescriptions(patient);
        patientDomainWrapper.setPatient(patient);
        model.addAttribute("labPrescriptions", labPrescriptions);
        model.addAttribute("patient", patientDomainWrapper);
        model.addAttribute("labSet", labSet);
        model.addAttribute("orderReasonsMap", orderReasonsMap);
        model.addAttribute("returnUrl", returnUrl);
        model.addAttribute("pihAppsUtils", new PihAppsUtils());
    }
}
