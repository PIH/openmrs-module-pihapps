package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;

public class LabOrderListPageController {

    public static final String PATIENT_GROUP_ACTIONS_EXTENSION_POINT = "org.openmrs.module.pihapps.labOrderList.patientGroupActions";

    /**
     * Extensions whose extensionParams.jsFunction is given the orders shown and returns, or resolves to, a map from order
     * uuid to a badge {text, cssClass, title, showOnPatientRow} to show beside the order's status
     */
    public static final String ORDER_BADGES_EXTENSION_POINT = "org.openmrs.module.pihapps.labOrderList.orderBadges";

    public void controller(PageModel model, UiSessionContext sessionContext,
                           @SpringBean AdtService adtService,
                           @SpringBean AppFrameworkService appFrameworkService,
                           @SpringBean LabOrderConfig labOrderConfig) {

        Location sessionLocation = sessionContext.getSessionLocation();
        model.addAttribute("sessionLocation", sessionLocation);
        Location visitLocation = null;
        if (sessionLocation != null) {
            visitLocation = adtService.getLocationThatSupportsVisits(sessionLocation);
        }
        model.addAttribute("visitLocationForSessionLocation", visitLocation);

        model.addAttribute("patientGroupActionExtensions", LabExtensions.getExtensions(appFrameworkService, PATIENT_GROUP_ACTIONS_EXTENSION_POINT));
        model.addAttribute("orderBadgeExtensions", LabExtensions.getExtensions(appFrameworkService, ORDER_BADGES_EXTENSION_POINT));
        model.addAttribute("canValidateResults", labOrderConfig.getRequireResultValidation()
                && Context.hasPrivilege(LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS));
    }

}
