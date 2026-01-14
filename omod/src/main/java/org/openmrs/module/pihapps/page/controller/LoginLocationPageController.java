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

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.LocationTagConfig;
import org.openmrs.module.pihapps.LocationTagWebConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

/**
 * Controls page which is used to set the user's current session location
 */
@Controller
public class LoginLocationPageController {

    private static Logger log = LoggerFactory.getLogger(LoginLocationPageController.class);

    public String get(PageModel model, UiUtils ui, UiSessionContext sessionContext,
                      HttpServletRequest request, HttpServletResponse response,
                      @SpringBean LocationTagConfig locationTagConfig) {

        model.addAttribute("locationTagConfig", locationTagConfig);
        model.addAttribute("authenticatedUser", sessionContext.getCurrentUser());
        Location currentVisitLocation = null;
        Location currentLoginLocation = null;
        if (!locationTagConfig.isLocationSetupRequired()) {
            List<Location> loginLocations = locationTagConfig.getValidLoginLocations();
            if (loginLocations.size() == 1) {
                return post(sessionContext, response, loginLocations.get(0), getReferer(currentLoginLocation, request));
            }
            currentLoginLocation = sessionContext.getSessionLocation();
            if (currentLoginLocation != null) {
                List<Location> visitLocations = locationTagConfig.getVisitLocationsForLocation(currentLoginLocation);
                if (visitLocations.size() == 1) {
                    currentVisitLocation = visitLocations.get(0);
                }
            }
        }
        model.addAttribute("currentVisitLocation", currentVisitLocation);
        model.addAttribute("currentLoginLocation", currentLoginLocation);
        model.addAttribute("returnUrl", encodeUrl(getReferer(currentLoginLocation, request)));
        return "loginLocation";
    }

    public String post(UiSessionContext sessionContext, HttpServletResponse response,
                       @RequestParam(value = "sessionLocation") Location sessionLocation,
                       @RequestParam(value = "returnUrl", required = false, defaultValue = "/") String returnUrl) {
        LocationTagWebConfig.setLoginLocation(sessionLocation, sessionContext, response);
        returnUrl = decodeUrl(returnUrl);
        return "redirect:" + returnUrl;
    }

    protected String getReferer(Location currentLoginLocation, HttpServletRequest request) {
        String returnUrl = "/";
        log.debug("Current login location: {}", currentLoginLocation);
        if (currentLoginLocation != null) {
            String referer = request.getHeader("Referer");
            log.debug("Referer: {}", referer);
            if (StringUtils.isNotBlank(referer)) {
                try {
                    URL refererUrl = new URL(referer);
                    String baseUrl = refererUrl.getProtocol() + "://" + refererUrl.getHost();
                    String port = ":" + refererUrl.getPort();
                    if (referer.contains(port)) {
                        baseUrl = baseUrl + port;
                    }
                    String baseUrlAndContextPath = baseUrl + "/" + WebConstants.WEBAPP_NAME;
                    log.debug("baseUrlAndContextPath: {}", baseUrlAndContextPath);
                    if (referer.startsWith(baseUrlAndContextPath)) {
                        returnUrl = referer.substring(baseUrlAndContextPath.length());
                    }
                }
                catch (Exception e) {
                    log.debug("Unable to parse referer into returnUrl: {}", e.getMessage());
                }
            }
        }
        log.debug("returnUrl: {}", returnUrl);
        return returnUrl;
    }

    protected String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        }
        catch (Exception e) {
            return url;
        }
    }

    protected String decodeUrl(String url) {
        try {
            return URLDecoder.decode(url, "UTF-8");
        }
        catch (Exception e) {
            return url;
        }
    }
}
