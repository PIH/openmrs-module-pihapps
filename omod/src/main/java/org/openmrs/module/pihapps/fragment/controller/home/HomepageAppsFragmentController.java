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
package org.openmrs.module.pihapps.fragment.controller.home;

import org.openmrs.module.appframework.context.AppContextModel;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.ExtensionConfig;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentModel;

import java.util.List;

/**
 * Renders apps for the current context onto the home page
 */
public class HomepageAppsFragmentController {

	public static final String HOME_PAGE_EXTENSION_POINT = "org.openmrs.referenceapplication.homepageLink";
	
	public void controller(FragmentModel model, UiSessionContext sessionContext, @SpringBean("extensionConfig") ExtensionConfig extensionConfig) {
        AppContextModel appContextModel = sessionContext.generateAppContextModel();
		List<Extension> extensions = extensionConfig.getExtensions(HOME_PAGE_EXTENSION_POINT, appContextModel);
		model.addAttribute("extensions", extensions);
	}
}
