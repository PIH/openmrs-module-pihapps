/*
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

package org.openmrs.module.pihapps.fragment.controller;

import org.openmrs.Location;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.appui.AppUiExtensions;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.LocationTagConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentModel;

import java.util.List;
import java.util.Map;

public class HeaderFragmentController {

    public void controller(@SpringBean AppFrameworkService appFrameworkService,
                           @SpringBean LocationTagConfig locationTagConfig,
                           UiSessionContext sessionContext,
                           UiUtils uiUtils,
                           FragmentModel fragmentModel) {

        List<Extension> exts = appFrameworkService.getExtensionsForCurrentUser(AppUiExtensions.HEADER_CONFIG_EXTENSION);
        Extension lowestOrderExtension = getLowestOrderExtenstion(exts);
        Map<String, Object> configSettings = null;
        if (lowestOrderExtension != null) {
            configSettings = lowestOrderExtension.getExtensionParams();
        }
        fragmentModel.addAttribute("configSettings", configSettings);

        StringBuilder locationName = new StringBuilder();
        Location loginLocation = sessionContext.getSessionLocation();
        if (loginLocation != null) {
            locationName.append(uiUtils.format(loginLocation));
            if (locationTagConfig.getValidVisitLocations().size() > 1) {
                for (Location visitLocation : locationTagConfig.getVisitLocationsForLocation(loginLocation)) {
                    if (!visitLocation.equals(loginLocation)) {
                        locationName.append(" - ").append(uiUtils.format(visitLocation));
                    }
                }
            }
        }
        fragmentModel.addAttribute("loginLocationName", locationName.toString());
    }

    public Extension getLowestOrderExtenstion(List<Extension> exts) {
        Extension lowestOrderExtension = exts.isEmpty() ? null : exts.get(0);
        for(Extension ext : exts) {
            if (lowestOrderExtension.getOrder() > ext.getOrder()) {
                lowestOrderExtension = ext;
            }
        }
        return lowestOrderExtension;
    }
}
