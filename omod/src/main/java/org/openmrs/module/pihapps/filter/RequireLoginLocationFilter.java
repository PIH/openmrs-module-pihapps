/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p>
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.pihapps.filter;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.LocationTagWebConfig;
import org.openmrs.module.pihapps.UrlPathMatcher;
import org.openmrs.ui.framework.WebConstants;
import org.openmrs.util.ConfigUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

/**
 * Redirects an authenticated user to the login location selection page if they do not have a login location set in their session
 */
public class RequireLoginLocationFilter implements Filter {

	private static final Logger log = Logger.getLogger(RequireLoginLocationFilter.class);

	public static final String LOGIN_LOCATION_PATH = "/pihapps/loginLocation.page";

	public static final String LOGIN_LOCATION_PAGE = "/" + WebConstants.CONTEXT_PATH + LOGIN_LOCATION_PATH;

	public static final List<String> WHITELIST = Arrays.asList(
			"*.js", "*.css", "*.gif", "*.jpg", "*.jpeg", "*.png", "*.ttf", "*.woff", "*.action", "/csrfguard",
			"/pihapps/admin/configureLoginLocations.page", "/pihapps/account/termsAndConditions.page", "/ws/rest/**/*"
	);

	public boolean disabled = false;

	@Override
	public void init(FilterConfig filterConfig) {
		String value = ConfigUtil.getRuntimeProperty("pihapps.disableRequireLoginLocationFilter");
		disabled = Boolean.parseBoolean(value);
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (!disabled) {
			if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
				HttpServletRequest httpRequest = (HttpServletRequest) request;
				HttpServletResponse httpResponse = (HttpServletResponse) response;
				HttpSession session = httpRequest.getSession();
				User currentUser = Context.getAuthenticatedUser();
				if (currentUser != null) {
					if (!isExcluded(httpRequest)) {
						if (LocationTagWebConfig.getLoginLocation(session) == null) {
							String redirectUrl = getLoginLocationPageUrl(httpRequest);
							log.debug("Redirecting " + currentUser + " from " + httpRequest.getRequestURI() + " to " + redirectUrl);
							httpResponse.sendRedirect(redirectUrl);
							return;
						}
					}
				}
			}
		}
		chain.doFilter(request, response);
	}

	public boolean isExcluded(HttpServletRequest httpRequest) {
		String uri = httpRequest.getRequestURI();
		if (uri.equals(LOGIN_LOCATION_PAGE)) {
			return true;
		}
		return UrlPathMatcher.urlMatchesAnyPattern(httpRequest, WHITELIST);
	}

	/**
	 * @return the login location page url, with a returnUrl to the requested page if this is a page the user loaded in
	 * the browser, so that they are returned to it once they choose a login location (eg. a page requested before login)
	 */
	public String getLoginLocationPageUrl(HttpServletRequest request) {
		if (!"GET".equalsIgnoreCase(request.getMethod()) || !isPageNavigation(request)) {
			return LOGIN_LOCATION_PAGE;
		}
		String returnUrl = request.getRequestURI();
		if (returnUrl.startsWith(request.getContextPath())) {
			returnUrl = returnUrl.substring(request.getContextPath().length());
		}
		if (StringUtils.isBlank(returnUrl) || returnUrl.equals("/")) {
			return LOGIN_LOCATION_PAGE;
		}
		if (StringUtils.isNotBlank(request.getQueryString())) {
			returnUrl = returnUrl + "?" + request.getQueryString();
		}
		try {
			return LOGIN_LOCATION_PAGE + "?returnUrl=" + URLEncoder.encode(returnUrl, "UTF-8");
		}
		catch (Exception e) {
			return LOGIN_LOCATION_PAGE;
		}
	}

	/**
	 * Browsers report page loads in fetch metadata headers, but only over https and to localhost, so otherwise fall
	 * back to a request for html that isn't marked as ajax.  This matches the check in the authentication module.
	 */
	protected boolean isPageNavigation(HttpServletRequest request) {
		if (request.getHeader("Sec-Purpose") != null || request.getHeader("Purpose") != null) {
			return false;  // A prefetch or prerender, which the user may never see
		}
		String fetchMode = request.getHeader("Sec-Fetch-Mode");
		if (fetchMode != null) {
			return "navigate".equals(fetchMode) && "document".equals(request.getHeader("Sec-Fetch-Dest"));
		}
		String accept = request.getHeader("Accept");
		return accept != null && accept.contains("text/html") && request.getHeader("X-Requested-With") == null;
	}

	@Override
	public void destroy() {
	}
}
