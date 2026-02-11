package org.openmrs.module.pihapps.page.controller.admin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.coreapps.CoreAppsConstants;
import org.openmrs.module.emrapi.account.AccountService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;

public class RolesAndPrivilegesPageController {

    protected static final Log log = LogFactory.getLog(RolesAndPrivilegesPageController.class);

    public String get(
            PageModel pageModel,
            @SpringBean("accountService") AccountService accountService) {

        pageModel.addAttribute("capabilities", accountService.getAllCapabilities());
        if (!Context.hasPrivilege(CoreAppsConstants.PRIVILEGE_SYSTEM_ADMINISTRATOR)) {
            return "redirect:/index.htm";
        }
        return "admin/rolesAndPrivileges";
    }
}
