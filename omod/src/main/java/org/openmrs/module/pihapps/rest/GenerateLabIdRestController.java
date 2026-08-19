package org.openmrs.module.pihapps.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.api.ProviderService;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.appui.UiSessionContext;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a new Lab ID using whichever LabIdGenerator implementation is registered,
 * for the given session location, if any
 */
@Controller
public class GenerateLabIdRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    LocationService locationService;

    @Autowired
    ProviderService providerService;

    @Autowired
    MessageSourceService messageSourceService;

    @Autowired(required = false)
    List<LabIdGenerator> labIdGenerators = new ArrayList<>();

    @RequestMapping(value = "/rest/v1/pihapps/labs/labIdGenerator", method = RequestMethod.POST)
    @ResponseBody
    public Object labIdGenerator(HttpServletRequest request) throws ResponseException {
        boolean enabled = false;
        String message = null;
        try {
            UiSessionContext sessionContext = new UiSessionContext(locationService, providerService, request);
            Location sessionLocation = sessionContext.getSessionLocation();
            if (sessionLocation == null) {
                message = messageSourceService.getMessage("pihapps.labId.noSessionLocation");
            }
            else {
                enabled = getLabIdGenerator(sessionLocation) != null;
            }
        }
        catch (Exception e) {
            message = e.getLocalizedMessage();
        }
        SimpleObject result = new SimpleObject();
        result.put("enabled", enabled);
        result.put("message", message);
        return result;
    }

    @RequestMapping(value = "/rest/v1/pihapps/labs/generateLabId", method = RequestMethod.POST)
    @ResponseBody
    public Object generateLabId(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        try {
            UiSessionContext sessionContext = new UiSessionContext(locationService, providerService, request);
            Location sessionLocation = sessionContext.getSessionLocation();
            if (sessionLocation == null) {
                throw new IllegalStateException(messageSourceService.getMessage("pihapps.labId.noSessionLocation"));
            }
            LabIdGenerator generator = getLabIdGenerator(sessionLocation);
            if (generator == null) {
                throw new IllegalStateException(messageSourceService.getMessage("pihapps.labId.notEnabled"));
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

    private LabIdGenerator getLabIdGenerator(Location location) {
        for (LabIdGenerator generator : labIdGenerators) {
            if (generator.isEnabled(location)) {
                return generator;
            }
        }
        return null;
    }
}
