package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.Order;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.module.pihapps.orders.OrderStatus;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;

import java.util.ArrayList;
import java.util.List;

public class LabWorkflowPageController {

    public void get(PageModel model, UiUtils ui, @SpringBean("pihAppsConfig") PihAppsConfig pihAppsConfig) {
        LabOrderConfig labOrderConfig = pihAppsConfig.getLabOrderConfig();
        model.addAttribute("pihAppsConfig", pihAppsConfig);
        model.addAttribute("labOrderConfig", labOrderConfig);
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("fulfillerStatuses", Order.FulfillerStatus.values());
    }
}
