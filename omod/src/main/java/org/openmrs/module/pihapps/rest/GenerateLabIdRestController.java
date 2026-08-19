package org.openmrs.module.pihapps.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.module.pihapps.labs.LabIdGenerator;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Generates a new Lab ID using whichever LabIdGenerator implementation is registered,
 * if any.
 */
@Controller
public class GenerateLabIdRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    PihAppsConfig pihAppsConfig;

    @Autowired
    LocationService locationService;

    @Autowired
    ProviderService providerService;

    @RequestMapping(value = "/rest/v1/pihapps/labs/generateLabId", method = RequestMethod.POST)
    @ResponseBody
    public Object generateLabId(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        try {
            LabIdGenerator generator = pihAppsConfig.getLabOrderConfig().resolveLabIdGenerator();
            if (generator == null) {
                throw new IllegalStateException("Lab ID auto-generation is not enabled");
            }
            UiSessionContext sessionContext = new UiSessionContext(locationService, providerService, request);
            Location sessionLocation = sessionContext.getSessionLocation();
            if (sessionLocation == null) {
                throw new IllegalStateException("Unable to generate Lab ID: no session location is set");
            }
            String labId = generator.generateLabId(sessionLocation);
            SimpleObject result = new SimpleObject();
            result.put("labId", labId);
            return result;
        }
        catch (Exception e) {
            log.warn("Error generating Lab ID", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return RestUtil.wrapErrorResponse(e, e.getLocalizedMessage());
        }
    }
}
