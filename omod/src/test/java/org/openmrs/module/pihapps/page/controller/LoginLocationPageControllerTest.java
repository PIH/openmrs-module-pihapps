package org.openmrs.module.pihapps.page.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.LocationTagConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LoginLocationPageControllerTest {

    LoginLocationPageController controller;
    UiSessionContext sessionContext;
    LocationTagConfig locationTagConfig;
    MockHttpServletRequest request;
    HttpServletResponse response;
    Location visitLocation;
    Location loginLocation1;
    Location loginLocation2;

    @BeforeEach
    public void setup() {
        controller = new LoginLocationPageController();
        sessionContext = mock(UiSessionContext.class);
        when(sessionContext.getCurrentUser()).thenReturn(new User(1));
        locationTagConfig = mock(LocationTagConfig.class);
        request = new MockHttpServletRequest("GET", "/openmrs/pihapps/loginLocation.page");
        request.setContextPath("/openmrs");
        response = mock(HttpServletResponse.class);
        visitLocation = new Location(1);
        loginLocation1 = new Location(2);
        loginLocation2 = new Location(3);
    }

    @Test
    public void get_shouldRedirectToReturnUrlIfOnlyOneLoginLocation() {
        setupLoginLocations(Collections.singletonList(loginLocation1));
        String result = get("/coreapps/clinicianfacing/patient.page?patientId=abc&app=x");
        assertThat(result, equalTo("redirect:/coreapps/clinicianfacing/patient.page?patientId=abc&app=x"));
        verify(sessionContext).setSessionLocation(loginLocation1);
    }

    @Test
    public void get_shouldRedirectToRootIfOnlyOneLoginLocationAndNoReturnUrl() {
        setupLoginLocations(Collections.singletonList(loginLocation1));
        assertThat(get(null), equalTo("redirect:/"));
    }

    @Test
    public void get_shouldRedirectToRootIfOnlyOneLoginLocationAndInvalidReturnUrl() {
        setupLoginLocations(Collections.singletonList(loginLocation1));
        assertThat(get("https://example.com/"), equalTo("redirect:/"));
    }

    @Test
    public void get_shouldPassReturnUrlToPageIfMultipleLoginLocations() {
        setupLoginLocations(Arrays.asList(loginLocation1, loginLocation2));
        PageModel model = new PageModel();
        String returnUrl = "/coreapps/clinicianfacing/patient.page?patientId=abc&app=x";
        String result = controller.get(model, mock(UiUtils.class), sessionContext, request, response, locationTagConfig, returnUrl);
        assertThat(result, equalTo("loginLocation"));
        assertThat(model.get("returnUrl"), equalTo("%2Fcoreapps%2Fclinicianfacing%2Fpatient.page%3FpatientId%3Dabc%26app%3Dx"));
    }

    @Test
    public void get_shouldUseRefererIfChangingLoginLocationAndNoReturnUrl() {
        setupLoginLocations(Arrays.asList(loginLocation1, loginLocation2));
        when(sessionContext.getSessionLocation()).thenReturn(loginLocation1);
        request.addHeader("Referer", "http://localhost:8080/openmrs/coreapps/findpatient/findPatient.page?app=x");
        PageModel model = new PageModel();
        controller.get(model, mock(UiUtils.class), sessionContext, request, response, locationTagConfig, null);
        assertThat(model.get("returnUrl"), equalTo("%2Fcoreapps%2Ffindpatient%2FfindPatient.page%3Fapp%3Dx"));
    }

    @Test
    public void post_shouldRedirectToDecodedReturnUrl() {
        String returnUrl = "%2Fcoreapps%2Fclinicianfacing%2Fpatient.page%3FpatientId%3Dabc%26app%3Dx";
        String result = controller.post(sessionContext, response, loginLocation1, returnUrl);
        assertThat(result, equalTo("redirect:/coreapps/clinicianfacing/patient.page?patientId=abc&app=x"));
        verify(sessionContext).setSessionLocation(loginLocation1);
        ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookie.capture());
        assertThat(cookie.getValue().getName(), equalTo("emr.lastSessionLocation"));
        assertThat(cookie.getValue().getValue(), equalTo("2"));
    }

    @Test
    public void post_shouldRedirectToRootForInvalidReturnUrl() {
        assertThat(controller.post(sessionContext, response, loginLocation1, "https%3A%2F%2Fexample.com"), equalTo("redirect:/"));
        assertThat(controller.post(sessionContext, response, loginLocation1, "%2F%2Fexample.com"), equalTo("redirect:/"));
    }

    @Test
    public void isValidReturnUrl_shouldAcceptPathsWithinApplication() {
        assertThat(controller.isValidReturnUrl("/"), is(true));
        assertThat(controller.isValidReturnUrl("/index.htm"), is(true));
        assertThat(controller.isValidReturnUrl("/coreapps/clinicianfacing/patient.page?patientId=abc&app=x"), is(true));
        assertThat(controller.isValidReturnUrl("/spa/home?return=../x"), is(true));
    }

    @Test
    public void isValidReturnUrl_shouldRejectUrlsOutsideApplication() {
        assertThat(controller.isValidReturnUrl(null), is(false));
        assertThat(controller.isValidReturnUrl(""), is(false));
        assertThat(controller.isValidReturnUrl("coreapps/home.page"), is(false));
        assertThat(controller.isValidReturnUrl("https://example.com/"), is(false));
        assertThat(controller.isValidReturnUrl("javascript:alert(1)"), is(false));
        assertThat(controller.isValidReturnUrl("//example.com/"), is(false));
        assertThat(controller.isValidReturnUrl("/\\example.com/"), is(false));
        assertThat(controller.isValidReturnUrl("/\t/example.com/"), is(false));
        assertThat(controller.isValidReturnUrl("/../manager/html"), is(false));
        assertThat(controller.isValidReturnUrl("/pihapps/loginLocation.page"), is(false));
    }

    private void setupLoginLocations(List<Location> loginLocations) {
        Map<Location, List<Location>> visitAndLoginLocations = new LinkedHashMap<>();
        visitAndLoginLocations.put(visitLocation, loginLocations);
        when(locationTagConfig.isLocationSetupRequired()).thenReturn(false);
        when(locationTagConfig.getValidVisitAndLoginLocations()).thenReturn(visitAndLoginLocations);
        when(locationTagConfig.getVisitLocationsForLocation(loginLocation1)).thenReturn(Collections.singletonList(visitLocation));
    }

    private String get(String returnUrl) {
        return controller.get(new PageModel(), mock(UiUtils.class), sessionContext, request, response, locationTagConfig, returnUrl);
    }
}
