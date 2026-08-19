package org.openmrs.module.pihapps.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openmrs.Location;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.LocationService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.module.pihapps.labs.LabIdGenerator;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenerateLabIdRestControllerTest {

    LocationService locationService;
    ProviderService providerService;
    GenerateLabIdRestController controller;

    @BeforeEach
    public void setup() {
        UserContext userContext = mock(UserContext.class);
        when(userContext.getAuthenticatedUser()).thenReturn(null);
        Context.setUserContext(userContext);

        locationService = mock(LocationService.class);
        providerService = mock(ProviderService.class);
        AdministrationService administrationService = mock(AdministrationService.class);

        ServiceContext serviceContext = ServiceContext.getInstance();
        serviceContext.setAdministrationService(administrationService);

        controller = new GenerateLabIdRestController();
        controller.locationService = locationService;
        controller.providerService = providerService;
    }

    @AfterEach
    public void tearDown() {
        Context.clearUserContext();
    }

    private MockHttpServletRequest requestForLocation(Location location) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UiSessionContext.LOCATION_SESSION_ATTRIBUTE, 1);
        request.setSession(session);
        when(locationService.getLocation(1)).thenReturn(location);
        return request;
    }

    @Test
    public void generateLabId_shouldReturn500WhenNoGeneratorRegistered() {
        PihAppsConfig pihAppsConfig = mock(PihAppsConfig.class);
        LabOrderConfig labOrderConfig = mock(LabOrderConfig.class);
        when(pihAppsConfig.getLabOrderConfig()).thenReturn(labOrderConfig);
        when(labOrderConfig.resolveLabIdGenerator()).thenReturn(null);
        controller.pihAppsConfig = pihAppsConfig;

        Location location = mock(Location.class);
        MockHttpServletRequest request = requestForLocation(location);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.generateLabId(request, response);

        assertThat(response.getStatus(), is(500));
    }

    @Test
    public void generateLabId_shouldReturnLabIdWhenGeneratorSucceeds() {
        PihAppsConfig pihAppsConfig = mock(PihAppsConfig.class);
        LabOrderConfig labOrderConfig = mock(LabOrderConfig.class);
        LabIdGenerator generator = mock(LabIdGenerator.class);
        when(pihAppsConfig.getLabOrderConfig()).thenReturn(labOrderConfig);
        when(labOrderConfig.resolveLabIdGenerator()).thenReturn(generator);
        when(generator.generateLabId(Mockito.any())).thenReturn("KIB-20260819-0001");
        controller.pihAppsConfig = pihAppsConfig;

        Location location = mock(Location.class);
        MockHttpServletRequest request = requestForLocation(location);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = controller.generateLabId(request, response);

        assertThat(response.getStatus(), is(200));
        assertThat(((SimpleObject) result).get("labId"), equalTo("KIB-20260819-0001"));
    }

    @Test
    public void generateLabId_shouldReturn500WhenGeneratorThrows() {
        PihAppsConfig pihAppsConfig = mock(PihAppsConfig.class);
        LabOrderConfig labOrderConfig = mock(LabOrderConfig.class);
        LabIdGenerator generator = mock(LabIdGenerator.class);
        when(pihAppsConfig.getLabOrderConfig()).thenReturn(labOrderConfig);
        when(labOrderConfig.resolveLabIdGenerator()).thenReturn(generator);
        when(generator.generateLabId(Mockito.any())).thenThrow(new IllegalStateException("No FOSA ID configured"));
        controller.pihAppsConfig = pihAppsConfig;

        Location location = mock(Location.class);
        MockHttpServletRequest request = requestForLocation(location);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.generateLabId(request, response);

        assertThat(response.getStatus(), is(500));
    }
}
