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

import org.apache.commons.lang.StringUtils;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.authentication.AuthenticationConfig;
import org.openmrs.module.authentication.web.TwoFactorAuthenticationScheme;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;

/**
 * Two Factor Alert Fragment for the home page
 */
public class TwoFactorAlertFragmentController {
	
	public void controller(FragmentModel model, FragmentConfiguration config) {
		User currentUser = Context.getAuthenticatedUser();
		boolean twoFactorEnabled = AuthenticationConfig.getAuthenticationScheme() instanceof TwoFactorAuthenticationScheme;
		String secondaryType = currentUser.getUserProperty(TwoFactorAuthenticationScheme.USER_PROPERTY_SECONDARY_TYPE);
		boolean userRequires2fa = twoFactorEnabled && StringUtils.isBlank(secondaryType);
		model.addAttribute("showBanner", userRequires2fa && getBooleanAttribute(config, "showBanner"));
		model.addAttribute("showDialog", userRequires2fa && getBooleanAttribute(config, "showDialog"));
	}

	boolean getBooleanAttribute(FragmentConfiguration config, String attributeName) {
		Object value = config.getAttribute(attributeName);
		if (value == null) {
			return true;
		}
		return Boolean.parseBoolean(value.toString());
	}
}
