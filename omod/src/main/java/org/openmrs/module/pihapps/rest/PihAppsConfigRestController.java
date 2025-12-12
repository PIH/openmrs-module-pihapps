package org.openmrs.module.pihapps.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides configuration and other endpoints for lab order entry
 */
@Controller
public class PihAppsConfigRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    PihAppsConfig pihAppsConfig;

    @RequestMapping(value = "/rest/v1/pihapps/config", method = RequestMethod.GET)
    @ResponseBody
    public SimpleObject getConfig(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        RequestContext context = RestUtil.getRequestContext(request, response, Representation.REF);
        return (SimpleObject) ConversionUtil.convertToRepresentation(pihAppsConfig, context.getRepresentation());
    }
}