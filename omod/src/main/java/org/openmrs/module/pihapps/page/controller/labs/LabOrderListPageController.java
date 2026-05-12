package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.Location;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;

public class LabOrderListPageController {

    public void controller(PageModel model, UiSessionContext sessionContext,
                           @SpringBean AdtService adtService) {

        Location sessionLocation = sessionContext.getSessionLocation();
        model.addAttribute("sessionLocation", sessionLocation);
        Location visitLocation = null;
        if (sessionLocation != null) {
            visitLocation = adtService.getLocationThatSupportsVisits(sessionLocation);
        }
        model.addAttribute("visitLocationForSessionLocation", visitLocation);
    }
}
