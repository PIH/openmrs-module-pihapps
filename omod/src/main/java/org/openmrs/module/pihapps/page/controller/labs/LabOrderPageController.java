package org.openmrs.module.pihapps.page.controller.labs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.module.pihapps.PihAppsUtils;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabOrderPageController {

    private static final Log log = LogFactory.getLog(LabOrderPageController.class);

    /**
     * Extensions whose extensionParams.provider and extensionParams.fragment name a fragment shown on the lab order
     * page, below the draft orders; it is given the patient. A fragment can follow the draft and act once the orders
     * are saved by adding to window.pihappsLabOrderListeners an object with onDraftChange(orderedConceptUuids) and/or
     * afterSave(savedEncounter), which may return a promise the page waits for before leaving.
     */
    public static final String FRAGMENTS_EXTENSION_POINT = "org.openmrs.module.pihapps.labOrder.fragments";

    public void get(PageModel model, UiUtils ui,
                      @InjectBeans PatientDomainWrapper patientDomainWrapper,
                      @RequestParam(value = "patient") Patient patient,
                      @RequestParam(value = "returnUrl", required = false) String returnUrl,
                      @SpringBean("pihAppsUtils") PihAppsUtils pihAppsUtils,
                      @SpringBean("pihAppsConfig") PihAppsConfig pihAppsConfig,
                      @SpringBean AppFrameworkService appFrameworkService) {

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("patient", patientDomainWrapper);
        model.addAttribute("labTestsByCategory", pihAppsConfig.getLabOrderConfig().getAvailableLabTestsByCategory());
        model.addAttribute("orderReasonsMap", pihAppsConfig.getLabOrderConfig().getOrderReasonsMap());
        model.addAttribute("returnUrl", returnUrl);
        model.addAttribute("pihAppsUtils", pihAppsUtils);
        model.addAttribute("pihAppsConfig", pihAppsConfig);
        model.addAttribute("labOrderConfig", pihAppsConfig.getLabOrderConfig());
        model.addAttribute("labOrderType", pihAppsConfig.getLabOrderConfig().getLabTestOrderType());
        model.addAttribute("labOrderFragments", getFragments(appFrameworkService));
    }

    private List<Map<String, Object>> getFragments(AppFrameworkService appFrameworkService) {
        List<Map<String, Object>> ret = new ArrayList<>();
        for (Extension ext : appFrameworkService.getExtensionsForCurrentUser(FRAGMENTS_EXTENSION_POINT)) {
            Map<String, Object> params = ext.getExtensionParams() != null ? ext.getExtensionParams() : new HashMap<>();
            if (params.get("provider") != null && params.get("fragment") != null) {
                Map<String, Object> fragment = new HashMap<>();
                fragment.put("provider", params.get("provider").toString());
                fragment.put("fragment", params.get("fragment").toString());
                ret.add(fragment);
            }
        }
        return ret;
    }
}
