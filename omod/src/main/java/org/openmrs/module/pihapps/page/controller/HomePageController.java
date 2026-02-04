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

import org.openmrs.module.appframework.context.AppContextModel;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.ExtensionConfig;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standard home page container that renders extensions
 */
public class HomePageController {

	private static final Logger log = LoggerFactory.getLogger(HomePageController.class);

	public static final String HOME_PAGE_FRAGMENTS = "pihapps.homepageFragments";
	
	public void controller(PageModel model, UiSessionContext sessionContext,
	                       @SpringBean ExtensionConfig extensionConfig) {
		
		sessionContext.requireAuthentication();
        AppContextModel appContextModel = sessionContext.generateAppContextModel();
		model.addAttribute("extensions", extensionConfig.getExtensions(HOME_PAGE_FRAGMENTS, appContextModel));
	}
}
