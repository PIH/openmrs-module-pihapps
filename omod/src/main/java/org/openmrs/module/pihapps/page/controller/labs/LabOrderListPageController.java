package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.Location;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabOrderListPageController {

    public static final String PATIENT_GROUP_ACTIONS_EXTENSION_POINT = "org.openmrs.module.pihapps.labOrderList.patientGroupActions";

    public void controller(PageModel model, UiSessionContext sessionContext,
                           @SpringBean AdtService adtService,
                           @SpringBean AppFrameworkService appFrameworkService) {

        Location sessionLocation = sessionContext.getSessionLocation();
        model.addAttribute("sessionLocation", sessionLocation);
        Location visitLocation = null;
        if (sessionLocation != null) {
            visitLocation = adtService.getLocationThatSupportsVisits(sessionLocation);
        }
        model.addAttribute("visitLocationForSessionLocation", visitLocation);

        List<Extension> extensions = appFrameworkService.getExtensionsForCurrentUser(PATIENT_GROUP_ACTIONS_EXTENSION_POINT);
        List<Map<String, Object>> patientGroupActionExtensions = new ArrayList<>();
        for (Extension ext : extensions) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", ext.getId());
            map.put("label", ext.getLabel());
            map.put("order", ext.getOrder());
            map.put("extensionParams", ext.getExtensionParams() != null ? ext.getExtensionParams() : new HashMap<>());
            patientGroupActionExtensions.add(map);
        }
        model.addAttribute("patientGroupActionExtensions", patientGroupActionExtensions);
    }
}
