package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.Order;
import org.openmrs.module.pihapps.orders.OrderStatus;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;

public class LabWorkflowPageController {

    public void get(PageModel model, UiUtils ui) {
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("fulfillerStatuses", Order.FulfillerStatus.values());
    }
}
