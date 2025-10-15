/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.pihapps.page.controller;

import org.openmrs.Location;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.LocationTagConfig;
import org.openmrs.module.pihapps.LocationTagWebConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Controls page which is used to set the user's current session location
 */
@Controller
public class LoginLocationPageController {

    public String get(PageModel model, UiUtils ui, UiSessionContext sessionContext, HttpServletResponse response,
                    @SpringBean LocationTagConfig locationTagConfig) {

        model.addAttribute("locationTagConfig", locationTagConfig);
        model.addAttribute("authenticatedUser", sessionContext.getCurrentUser());

        if (!locationTagConfig.isLocationSetupRequired()) {
            List<Location> loginLocations = locationTagConfig.getValidLoginLocations();
            if (loginLocations.size() == 1) {
                return post(sessionContext, response, loginLocations.get(0));
            }
        }
        return "loginLocation";
    }

    public String post(UiSessionContext sessionContext, HttpServletResponse response,
                       @RequestParam("sessionLocation") Location sessionLocation) {
        LocationTagWebConfig.setLoginLocation(sessionLocation, sessionContext, response);
        return "redirect:/";
    }
}
