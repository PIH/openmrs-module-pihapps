package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.Patient;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatientLabResultsPageController {

    /**
     * Extensions whose extensionParams.concept is an obs group concept, whose results are shown as one row, and whose
     * extensionParams.jsFunction is given the obs group and returns, or resolves to, the html or element to show as
     * its result
     */
    public static final String OBS_GROUP_RENDERERS_EXTENSION_POINT = "org.openmrs.module.pihapps.patientLabResults.obsGroupRenderers";

    public void get(PageModel model, UiUtils ui,
                      @InjectBeans PatientDomainWrapper patientDomainWrapper,
                      @RequestParam(value = "patient") Patient patient,
                      @SpringBean("pihAppsConfig") PihAppsConfig pihAppsConfig,
                      @SpringBean AppFrameworkService appFrameworkService) {

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("patient", patientDomainWrapper);
        model.addAttribute("pihAppsConfig", pihAppsConfig);
        model.addAttribute("obsGroupRendererExtensions", getExtensions(appFrameworkService, OBS_GROUP_RENDERERS_EXTENSION_POINT));
    }

    private List<Map<String, Object>> getExtensions(AppFrameworkService appFrameworkService, String extensionPoint) {
        List<Map<String, Object>> ret = new ArrayList<>();
        for (Extension ext : appFrameworkService.getExtensionsForCurrentUser(extensionPoint)) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", ext.getId());
            map.put("label", ext.getLabel());
            map.put("order", ext.getOrder());
            map.put("extensionParams", ext.getExtensionParams() != null ? ext.getExtensionParams() : new HashMap<>());
            ret.add(map);
        }
        return ret;
    }
}
