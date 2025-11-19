package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.Order;
import org.openmrs.module.pihapps.labs.LabOrderConfig;
import org.openmrs.module.pihapps.PihAppsConfig;
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

        // Add fulfiller statuses in the order we want them displayed
        List<Order.FulfillerStatus> fulfillerStatuses = new ArrayList<>();
        fulfillerStatuses.add(Order.FulfillerStatus.RECEIVED);
        fulfillerStatuses.add(Order.FulfillerStatus.IN_PROGRESS);
        fulfillerStatuses.add(Order.FulfillerStatus.COMPLETED);
        for (Order.FulfillerStatus fulfillerStatus : Order.FulfillerStatus.values()) {
            if (!fulfillerStatuses.contains(fulfillerStatus)) {
                fulfillerStatuses.add(fulfillerStatus);
            }
        }
        model.addAttribute("fulfillerStatuses", fulfillerStatuses);
    }
}
