package org.openmrs.module.pihapps.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequireLoginLocationFilterTest {

    static final String LOGIN_LOCATION_PAGE = RequireLoginLocationFilter.LOGIN_LOCATION_PAGE;

    UserContext userContext;
    RequireLoginLocationFilter filter;

    @BeforeEach
    public void setup() {
        userContext = mock(UserContext.class);
        when(userContext.getAuthenticatedUser()).thenReturn(new User(1));
        Context.setUserContext(userContext);
        filter = new RequireLoginLocationFilter();
    }

    @AfterEach
    public void tearDown() {
        Context.clearUserContext();
    }

    @Test
    public void shouldRedirectPageRequestToLoginLocationPageWithReturnUrl() throws Exception {
        MockHttpServletRequest request = pageRequest("/openmrs/coreapps/clinicianfacing/patient.page", "patientId=abc-123&app=x");
        MockHttpServletResponse response = doFilter(request);
        String expectedReturnUrl = "%2Fcoreapps%2Fclinicianfacing%2Fpatient.page%3FpatientId%3Dabc-123%26app%3Dx";
        assertThat(response.getRedirectedUrl(), equalTo(LOGIN_LOCATION_PAGE + "?returnUrl=" + expectedReturnUrl));
    }

    @Test
    public void shouldRedirectPageRequestWithoutQueryStringWithReturnUrl() throws Exception {
        MockHttpServletRequest request = pageRequest("/openmrs/pihapps/someOther.page", null);
        MockHttpServletResponse response = doFilter(request);
        assertThat(response.getRedirectedUrl(), equalTo(LOGIN_LOCATION_PAGE + "?returnUrl=%2Fpihapps%2FsomeOther.page"));
    }

    @Test
    public void shouldIdentifyPageNavigationFromFetchMetadataHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/openmrs/pihapps/someOther.page");
        request.setContextPath("/openmrs");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        request.addHeader("Sec-Fetch-Dest", "document");
        MockHttpServletResponse response = doFilter(request);
        assertThat(response.getRedirectedUrl(), equalTo(LOGIN_LOCATION_PAGE + "?returnUrl=%2Fpihapps%2FsomeOther.page"));
    }

    @Test
    public void shouldNotIncludeReturnUrlForContextRoot() throws Exception {
        MockHttpServletResponse response = doFilter(pageRequest("/openmrs/", null));
        assertThat(response.getRedirectedUrl(), equalTo(LOGIN_LOCATION_PAGE));
    }

    @Test
    public void shouldNotIncludeReturnUrlForAjaxRequest() throws Exception {
        MockHttpServletRequest request = pageRequest("/openmrs/coreapps/clinicianfacing/patient.page", null);
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = doFilter(request);
        assertThat(response.getRedirectedUrl(), equalTo(LOGIN_LOCATION_PAGE));
    }

    @Test
    public void shouldNotIncludeReturnUrlForNonPageFetch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/openmrs/pihapps/someOther.page");
        request.setContextPath("/openmrs");
        request.addHeader("Sec-Fetch-Mode", "cors");
        request.addHeader("Sec-Fetch-Dest", "empty");
        MockHttpServletResponse response = doFilter(request);
        assertThat(response.getRedirectedUrl(), equalTo(LOGIN_LOCATION_PAGE));
    }

    @Test
    public void shouldNotIncludeReturnUrlForPrefetch() throws Exception {
        MockHttpServletRequest request = pageRequest("/openmrs/pihapps/someOther.page", null);
        request.addHeader("Sec-Purpose", "prefetch");
        MockHttpServletResponse response = doFilter(request);
        assertThat(response.getRedirectedUrl(), equalTo(LOGIN_LOCATION_PAGE));
    }

    @Test
    public void shouldNotIncludeReturnUrlForPost() throws Exception {
        MockHttpServletRequest request = pageRequest("/openmrs/pihapps/someOther.page", null);
        request.setMethod("POST");
        MockHttpServletResponse response = doFilter(request);
        assertThat(response.getRedirectedUrl(), equalTo(LOGIN_LOCATION_PAGE));
    }

    @Test
    public void shouldNotRedirectIfNotAuthenticated() throws Exception {
        when(userContext.getAuthenticatedUser()).thenReturn(null);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(pageRequest("/openmrs/pihapps/someOther.page", null), response, chain);
        assertThat(response.getRedirectedUrl(), nullValue());
        assertThat(chain.getRequest(), notNullValue());
    }

    @Test
    public void shouldNotRedirectWhitelistedRequest() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(pageRequest("/openmrs/ws/rest/v1/session", null), response, chain);
        assertThat(response.getRedirectedUrl(), nullValue());
        assertThat(chain.getRequest(), notNullValue());
    }

    private MockHttpServletRequest pageRequest(String requestUri, String queryString) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setContextPath("/openmrs");
        request.setServletPath(requestUri.substring("/openmrs".length()));
        request.setQueryString(queryString);
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        return request;
    }

    private MockHttpServletResponse doFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
