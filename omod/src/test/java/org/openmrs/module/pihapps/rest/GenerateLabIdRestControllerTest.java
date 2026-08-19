package org.openmrs.module.pihapps.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.api.context.UserContext;
import org.openmrs.api.AdministrationService;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.labs.LabIdGenerator;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenerateLabIdRestControllerTest {

    LocationService locationService;
    ProviderService providerService;
    MessageSourceService messageSourceService;
    GenerateLabIdRestController controller;
    Location location;

    @BeforeEach
    public void setup() {
        UserContext userContext = mock(UserContext.class);
        when(userContext.getAuthenticatedUser()).thenReturn(null);
        Context.setUserContext(userContext);

        // RestUtil.wrapErrorResponse() internally calls Context.getAdministrationService() with no
        // fallback, so it must be available for the generateLabId error-response tests to work
        AdministrationService administrationService = mock(AdministrationService.class);
        ServiceContext.getInstance().setAdministrationService(administrationService);

        locationService = mock(LocationService.class);
        providerService = mock(ProviderService.class);
        messageSourceService = mock(MessageSourceService.class);
        when(messageSourceService.getMessage(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        controller = new GenerateLabIdRestController();
        controller.locationService = locationService;
        controller.providerService = providerService;
        controller.messageSourceService = messageSourceService;
        controller.labIdGenerators = new ArrayList<>();

        location = mock(Location.class);
    }

    @AfterEach
    public void tearDown() {
        Context.clearUserContext();
        ServiceContext.getInstance().setAdministrationService(null);
    }

    private MockHttpServletRequest requestWithSessionLocation(Location location) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UiSessionContext.LOCATION_SESSION_ATTRIBUTE, 1);
        request.setSession(session);
        when(locationService.getLocation(1)).thenReturn(location);
        return request;
    }

    private MockHttpServletRequest requestWithNoSessionLocation() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        return request;
    }

    // labIdGenerator (check) endpoint

    @Test
    public void labIdGenerator_shouldReturnDisabledWhenNoGeneratorRegistered() {
        MockHttpServletRequest request = requestWithSessionLocation(location);

        SimpleObject result = (SimpleObject) controller.labIdGenerator(request);

        assertThat(result.get("enabled"), is(false));
        assertThat(result.get("message"), is(nullValue()));
    }

    @Test
    public void labIdGenerator_shouldReturnEnabledWhenAGeneratorIsEnabledForLocation() {
        LabIdGenerator generator = mock(LabIdGenerator.class);
        when(generator.isEnabled(location)).thenReturn(true);
        controller.labIdGenerators = Collections.singletonList(generator);
        MockHttpServletRequest request = requestWithSessionLocation(location);

        SimpleObject result = (SimpleObject) controller.labIdGenerator(request);

        assertThat(result.get("enabled"), is(true));
        assertThat(result.get("message"), is(nullValue()));
    }

    @Test
    public void labIdGenerator_shouldReturnDisabledWhenGeneratorIsNotEnabledForLocation() {
        LabIdGenerator generator = mock(LabIdGenerator.class);
        when(generator.isEnabled(location)).thenReturn(false);
        controller.labIdGenerators = Collections.singletonList(generator);
        MockHttpServletRequest request = requestWithSessionLocation(location);

        SimpleObject result = (SimpleObject) controller.labIdGenerator(request);

        assertThat(result.get("enabled"), is(false));
        assertThat(result.get("message"), is(nullValue()));
    }

    @Test
    public void labIdGenerator_shouldReturnDisabledWithMessageWhenNoSessionLocation() {
        MockHttpServletRequest request = requestWithNoSessionLocation();

        SimpleObject result = (SimpleObject) controller.labIdGenerator(request);

        assertThat(result.get("enabled"), is(false));
        assertThat(result.get("message"), equalTo("pihapps.labId.noSessionLocation"));
    }

    @Test
    public void labIdGenerator_shouldReturnDisabledWithMessageWhenCheckingEnablementThrows() {
        LabIdGenerator generator = mock(LabIdGenerator.class);
        when(generator.isEnabled(location)).thenThrow(new IllegalStateException("boom"));
        controller.labIdGenerators = Collections.singletonList(generator);
        MockHttpServletRequest request = requestWithSessionLocation(location);

        SimpleObject result = (SimpleObject) controller.labIdGenerator(request);

        assertThat(result.get("enabled"), is(false));
        assertThat(result.get("message"), equalTo("boom"));
    }

    // generateLabId endpoint

    @Test
    public void generateLabId_shouldReturn500WhenNoGeneratorRegistered() {
        MockHttpServletRequest request = requestWithSessionLocation(location);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.generateLabId(request, response);

        assertThat(response.getStatus(), is(500));
    }

    @Test
    public void generateLabId_shouldReturn500WhenNoGeneratorIsEnabledForLocation() {
        LabIdGenerator generator = mock(LabIdGenerator.class);
        when(generator.isEnabled(location)).thenReturn(false);
        controller.labIdGenerators = Collections.singletonList(generator);
        MockHttpServletRequest request = requestWithSessionLocation(location);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.generateLabId(request, response);

        assertThat(response.getStatus(), is(500));
    }

    @Test
    public void generateLabId_shouldReturnLabIdWhenGeneratorSucceeds() {
        LabIdGenerator generator = mock(LabIdGenerator.class);
        when(generator.isEnabled(location)).thenReturn(true);
        when(generator.generateLabId(location)).thenReturn("KIB-20260819-0001");
        controller.labIdGenerators = Collections.singletonList(generator);
        MockHttpServletRequest request = requestWithSessionLocation(location);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = controller.generateLabId(request, response);

        assertThat(response.getStatus(), is(200));
        assertThat(((SimpleObject) result).get("labId"), equalTo("KIB-20260819-0001"));
        Mockito.verify(generator).generateLabId(location);
    }

    @Test
    public void generateLabId_shouldReturn500WhenGeneratorThrows() {
        LabIdGenerator generator = mock(LabIdGenerator.class);
        when(generator.isEnabled(location)).thenReturn(true);
        when(generator.generateLabId(location)).thenThrow(new IllegalStateException("No FOSA ID configured"));
        controller.labIdGenerators = Collections.singletonList(generator);
        MockHttpServletRequest request = requestWithSessionLocation(location);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.generateLabId(request, response);

        assertThat(response.getStatus(), is(500));
    }

    @Test
    public void generateLabId_shouldReturn500WhenSessionLocationIsNull() {
        MockHttpServletRequest request = requestWithNoSessionLocation();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.generateLabId(request, response);

        assertThat(response.getStatus(), is(500));
    }
}
