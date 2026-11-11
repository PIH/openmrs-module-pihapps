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

    /**
     * Extensions whose extensionParams.jsFunction is given the orders shown and returns, or resolves to, a map from order
     * uuid to a badge {text, cssClass, title, showOnPatientRow} to show beside the order's status
     */
    public static final String ORDER_BADGES_EXTENSION_POINT = "org.openmrs.module.pihapps.labOrderList.orderBadges";

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

        model.addAttribute("patientGroupActionExtensions", getExtensions(appFrameworkService, PATIENT_GROUP_ACTIONS_EXTENSION_POINT));
        model.addAttribute("orderBadgeExtensions", getExtensions(appFrameworkService, ORDER_BADGES_EXTENSION_POINT));
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
