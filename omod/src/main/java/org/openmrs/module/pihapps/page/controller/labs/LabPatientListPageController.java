package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.Location;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class LabPatientListPageController {

    public void get(PageModel model, UiUtils ui, UiSessionContext sessionContext,
                      @RequestParam(value = "status", required = false) OrderFulfillmentStatus status,
                      @SpringBean("pihAppsConfig") PihAppsConfig pihAppsConfig,
                      @SpringBean AdtService adtService) {

        model.addAttribute("orderFulfillmentStatus", status == null ? OrderFulfillmentStatus.AWAITING_FULFILLMENT : status);
        model.addAttribute("pihAppsConfig", pihAppsConfig);

        Location sessionLocation = sessionContext.getSessionLocation();
        model.addAttribute("sessionLocation", sessionLocation);
        Location visitLocation = null;
        if (sessionLocation != null) {
            visitLocation = adtService.getLocationThatSupportsVisits(sessionLocation);
        }
        model.addAttribute("visitLocationForSessionLocation", visitLocation);
    }
}
