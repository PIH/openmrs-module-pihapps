package org.openmrs.module.pihapps.page.controller.account;

import org.openmrs.api.context.Context;
import org.openmrs.module.coreapps.CoreAppsConstants;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;

public class ManageAccountsPageController {

    public String get(PageModel model, @SpringBean("pihAppsConfig") PihAppsConfig pihAppsConfig) {
        if (!Context.hasPrivilege(CoreAppsConstants.PRIVILEGE_SYSTEM_ADMINISTRATOR)) {
            return "redirect:/index.htm";
        }
        model.addAttribute("pihAppsConfig", pihAppsConfig);
        return "account/manageAccounts";
    }
}
