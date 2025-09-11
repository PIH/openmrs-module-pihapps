package org.openmrs.module.pihapps.page.controller.labs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.pihapps.LabOrderConfig;
import org.openmrs.module.pihapps.PihAppsUtils;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class LabOrderPageController {

    private static final Log log = LogFactory.getLog(LabOrderPageController.class);

    public void get(PageModel model, UiUtils ui,
                      @InjectBeans PatientDomainWrapper patientDomainWrapper,
                      @RequestParam(value = "patient") Patient patient,
                      @RequestParam(value = "returnUrl", required = false) String returnUrl,
                      @SpringBean("labOrderConfig") LabOrderConfig labOrderConfig) {

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("patient", patientDomainWrapper);
        model.addAttribute("labSet", labOrderConfig.getLabOrderablesConceptSet());
        model.addAttribute("orderReasonsMap", labOrderConfig.getOrderReasonsMap());
        model.addAttribute("returnUrl", returnUrl);
        model.addAttribute("pihAppsUtils", new PihAppsUtils());
    }
}
