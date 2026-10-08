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
import org.openmrs.module.pihapps.filter.RequireLoginLocationFilter;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Controls page which is used to set the user's current session location
 */
@Controller
public class LoginLocationPageController {

    private static Logger log = LoggerFactory.getLogger(LoginLocationPageController.class);

    private static final Pattern WHITESPACE_OR_CONTROL = Pattern.compile("[\\s\\p{Cntrl}]");

    public String get(PageModel model, UiUtils ui, UiSessionContext sessionContext,
                      HttpServletRequest request, HttpServletResponse response,
                      @SpringBean LocationTagConfig locationTagConfig,
                      @RequestParam(value = "returnUrl", required = false) String returnUrl) {

        model.addAttribute("locationTagConfig", locationTagConfig);
        model.addAttribute("authenticatedUser", sessionContext.getCurrentUser());
        Location currentVisitLocation = null;
        Location currentLoginLocation = null;
        Map<Location, List<Location>> visitAndLoginLocations;

        if (!locationTagConfig.isLocationSetupRequired()) {
            Map<Location, List<Location>> all = locationTagConfig.getValidVisitAndLoginLocations();

            String allowedProp = sessionContext.getCurrentUser().getUserProperty("allowedVisitLocations");
            if (StringUtils.isNotBlank(allowedProp)) {
                Set<String> allowedUuids = new HashSet<>(Arrays.asList(allowedProp.split(",")));
                Map<Location, List<Location>> filtered = new LinkedHashMap<>();
                for (Map.Entry<Location, List<Location>> entry : all.entrySet()) {
                    if (allowedUuids.contains(entry.getKey().getUuid())) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                visitAndLoginLocations = filtered;
            } else {
                visitAndLoginLocations = all;
            }

            List<Location> allLoginLocations = new ArrayList<>();
            for (List<Location> locs : visitAndLoginLocations.values()) {
                allLoginLocations.addAll(locs);
            }
            if (allLoginLocations.size() == 1) {
                String redirectUrl = getReturnUrl(returnUrl, currentLoginLocation, request);
                return setLoginLocationAndRedirect(sessionContext, response, allLoginLocations.get(0), redirectUrl);
            }

            currentLoginLocation = sessionContext.getSessionLocation();
            if (currentLoginLocation != null) {
                List<Location> visitLocations = locationTagConfig.getVisitLocationsForLocation(currentLoginLocation);
                if (visitLocations.size() == 1) {
                    Location vl = visitLocations.get(0);
                    if (visitAndLoginLocations.containsKey(vl)) {
                        currentVisitLocation = vl;
                    }
                }
            }
        } else {
            visitAndLoginLocations = new LinkedHashMap<>();
        }

        model.addAttribute("visitAndLoginLocations", visitAndLoginLocations);
        model.addAttribute("currentVisitLocation", currentVisitLocation);
        model.addAttribute("currentLoginLocation", currentLoginLocation);
        model.addAttribute("returnUrl", encodeUrl(getReturnUrl(returnUrl, currentLoginLocation, request)));
        return "loginLocation";
    }

    public String post(UiSessionContext sessionContext, HttpServletResponse response,
                       @RequestParam(value = "sessionLocation") Location sessionLocation,
                       @RequestParam(value = "returnUrl", required = false, defaultValue = "/") String returnUrl) {
        return setLoginLocationAndRedirect(sessionContext, response, sessionLocation, decodeUrl(returnUrl));
    }

    protected String setLoginLocationAndRedirect(UiSessionContext sessionContext, HttpServletResponse response,
                                                 Location sessionLocation, String returnUrl) {
        LocationTagWebConfig.setLoginLocation(sessionLocation, sessionContext, response);
        if (!isValidReturnUrl(returnUrl)) {
            log.debug("Not redirecting to invalid returnUrl: {}", returnUrl);
            returnUrl = "/";
        }
        // Spring treats {name} in a redirect url as a template variable, so braces in the url (eg. in a query) are encoded
        return "redirect:" + returnUrl.replace("{", "%7B").replace("}", "%7D");
    }

    /**
     * @return the returnUrl requested (eg. by RequireLoginLocationFilter for a page requested before login), otherwise
     * the page the user came from to change their login location.  It is validated before redirecting to it.
     */
    protected String getReturnUrl(String requestedReturnUrl, Location currentLoginLocation, HttpServletRequest request) {
        if (StringUtils.isNotBlank(requestedReturnUrl)) {
            return requestedReturnUrl;
        }
        return getReferer(currentLoginLocation, request);
    }

    /**
     * @return true for a path within this application, relative to the context path, other than this page or a
     * logout url.  Rejects anything a redirect would take to another host: a url with a scheme (eg. http:), or starting
     * // or /\ (which browsers treat as //), or with whitespace or control characters (which browsers remove).
     * The path is checked as the browser and server would see it: decoded, without path parameters (eg. ;jsessionid=),
     * and with dot segments resolved.  This matches the check the authentication module makes on its return urls.
     */
    protected boolean isValidReturnUrl(String url) {
        if (StringUtils.isBlank(url) || !url.startsWith("/") || url.startsWith("//") || url.startsWith("/\\")) {
            return false;
        }
        if (WHITESPACE_OR_CONTROL.matcher(url).find()) {
            return false;
        }
        String path;
        try {
            String decoded = UriUtils.decode(url.split("[?#]", 2)[0], "UTF-8").replaceAll(";[^/]*", "");
            path = org.springframework.util.StringUtils.cleanPath(decoded).toLowerCase();
        }
        catch (Exception e) {
            return false;
        }
        String loginLocationPath = RequireLoginLocationFilter.LOGIN_LOCATION_PATH.toLowerCase();
        return !path.contains("..") && !path.contains("logout") && !path.contains(loginLocationPath);
    }

    protected String getReferer(Location currentLoginLocation, HttpServletRequest request) {
        String returnUrl = "/";
        log.debug("Current login location: {}", currentLoginLocation);
        if (currentLoginLocation != null) {
            String referer = request.getHeader("Referer");
            log.debug("Referer: {}", referer);
            if (StringUtils.isNotBlank(referer)) {
                if (!referer.contains(RequireLoginLocationFilter.LOGIN_LOCATION_PATH)) {
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
                    } catch (Exception e) {
                        log.debug("Unable to parse referer into returnUrl: {}", e.getMessage());
                    }
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
