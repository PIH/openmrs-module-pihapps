package org.openmrs.module.pihapps.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.EncounterType;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.module.pihapps.PihAppsService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every encounter type in the system, retired ones included, with how many encounters of that type
 * exist. Which types an implementation actually records against varies a great deal
 * by country, so a client offering a choice of type uses this to leave out the ones nothing has
 * been recorded against.
 *
 * <pre>
 * GET /openmrs/ws/rest/v1/pihapps/encounterTypeCount
 * GET /openmrs/ws/rest/v1/pihapps/encounterTypeCount?includeVoided=true
 * GET /openmrs/ws/rest/v1/pihapps/encounterTypeCount?v=custom:(uuid,display,retired)
 * </pre>
 *
 * <p>`includeVoided` decides whether voided encounters count towards their type, and is off unless
 * asked for. An audit wants it on, so that a type whose encounters have all been deleted is still
 * offered.
 *
 * <p>The response is a `results` list ordered by type name, each entry holding the type, rendered
 * in the representation `v` asks for and the ref representation otherwise, and its `count`. A type
 * with no encounters is listed with a count of zero rather than left out.
 *
 * <p>The counts are of the whole encounter table, so this is not paged and takes no filters.
 */
@Controller
public class PihAppsEncounterTypeCountRestController {

    protected Log log = LogFactory.getLog(getClass());

    @Autowired
    private PihAppsService pihAppsService;

    @RequestMapping(value = "/rest/v1/pihapps/encounterTypeCount", method = RequestMethod.GET)
    @ResponseBody
    public Object getEncounterTypeCounts(HttpServletRequest request, HttpServletResponse response,
                                         @RequestParam(value = "includeVoided", required = false,
                                                 defaultValue = "false") boolean includeVoided)
            throws ResponseException {
        try {
            RequestContext context = RestUtil.getRequestContext(request, response, Representation.REF);
            List<SimpleObject> results = new ArrayList<>();
            for (Map.Entry<EncounterType, Long> entry : pihAppsService.getEncounterTypeCounts(includeVoided).entrySet()) {
                SimpleObject result = new SimpleObject();
                result.add("encounterType",
                        ConversionUtil.convertToRepresentation(entry.getKey(), context.getRepresentation()));
                result.add("count", entry.getValue());
                results.add(result);
            }
            return new SimpleObject().add("results", results);
        }
        catch (APIAuthenticationException e) {
            // Left to the web layer, which answers it as 401 or 403 rather than as a server error.
            throw e;
        }
        catch (Exception e) {
            log.error("Failed to count encounters by encounter type", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return RestUtil.wrapErrorResponse(e, "Failed to count encounters by encounter type");
        }
    }
}
