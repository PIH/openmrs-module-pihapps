package org.openmrs.module.pihapps.page.controller.account;

import org.apache.commons.lang.BooleanUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.coreapps.CoreAppsConstants;
import org.openmrs.module.emrapi.account.AccountDomainWrapper;
import org.openmrs.module.emrapi.account.AccountSearchCriteria;
import org.openmrs.module.emrapi.account.AccountService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

public class ManageAccountsPageController {

    public String get(PageModel model, HttpServletRequest request,
                      @RequestParam(value = "nameOrIdentifier", required = false) String nameOrIdentifier,
                      @RequestParam(value = "showOnlyEnabledUsers", required = false, defaultValue = "false") Boolean showOnlyEnabledUsers,
                      @SpringBean("accountService") AccountService accountService) {

        if (!Context.hasPrivilege(CoreAppsConstants.PRIVILEGE_SYSTEM_ADMINISTRATOR)) {
             return "redirect:/index.htm";
        }

        Set<?> params = request.getParameterMap().keySet();
        List<AccountDomainWrapper> accounts = null;
        if (params.contains("nameOrIdentifier") || params.contains("showOnlyEnabledUsers")) {
            AccountSearchCriteria criteria = new AccountSearchCriteria();
            criteria.setNameOrIdentifier(nameOrIdentifier);
            accounts = accountService.getAccounts(criteria);
            accounts.removeIf(account -> showOnlyEnabledUsers && BooleanUtils.isNotTrue(account.getUserEnabled()));
        }

        model.addAttribute("accounts", accounts);
        model.addAttribute("nameOrIdentifier", nameOrIdentifier);
        model.addAttribute("showOnlyEnabledUsers", showOnlyEnabledUsers);
        return "account/manageAccounts";
    }
}
