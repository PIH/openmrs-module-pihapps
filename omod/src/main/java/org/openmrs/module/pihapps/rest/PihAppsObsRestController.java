package org.openmrs.module.pihapps.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchResult;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.response.InvalidSearchException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

/**
 * Searches observations by the user who created them or the user who voided them, which the core
 * REST API cannot do: neither the obs resource, the observation search handler nor core's own
 * {@link org.openmrs.parameter.ObsSearchCriteria} has a creator or voidedBy field, so those columns
 * can be read off an observation but not searched on.
 *
 * <p>Results are paged and ordered as `sortBy` asks, and each observation is rendered by the
 * standard obs resource, so `v` behaves as it does anywhere else in the REST API and defaults to
 * the same thing. An audit wants `auditInfo` — the creator and the voiding user with their
 * timestamps — which the default representation leaves out, so it asks for it:
 *
 * <pre>
 * GET /openmrs/ws/rest/v1/pihapps/obs?createdBy=&lt;uuid&gt;&amp;limit=20&amp;totalCount=true
 * GET /openmrs/ws/rest/v1/pihapps/obs?createdBy=&lt;uuid&gt;&amp;v=custom:(uuid,display,auditInfo)
 * GET /openmrs/ws/rest/v1/pihapps/obs?voidedBy=cd8a4b8e-...&amp;v=custom:(uuid,concept:(display),auditInfo)
 * GET /openmrs/ws/rest/v1/pihapps/obs?createdBy=&lt;uuid&gt;&amp;startDate=2026-09-01&amp;endDate=2026-09-30
 * GET /openmrs/ws/rest/v1/pihapps/obs?voidedBy=&lt;uuid&gt;&amp;includeVoided=true
 * GET /openmrs/ws/rest/v1/pihapps/obs?createdBy=&lt;uuid&gt;&amp;sortBy=dateCreated-desc&amp;sortBy=obsId-desc
 * </pre>
 *
 * <p>`createdBy` and `voidedBy` are bound by core's property editors, so each takes a uuid or a
 * primary key.
 *
 * <p>`includeVoided` decides whether voided observations come back alongside the surviving ones,
 * and is off unless asked for. An audit normally wants them on: a `voidedBy` search returns nothing
 * without them, and an auditor looking at what a user created wants to see what has since been
 * deleted just as much as what survives. Callers tell the two apart by each observation's voided
 * flag.
 *
 * <p>`sortBy` takes `field-direction`, or just `field` for ascending, and may be given several
 * times to order by more than one. Nothing is sorted unless asked, and a page without an ordering
 * is not deterministic, so a client that pages should name one ending in something unique such as
 * `obsId`. An audit wants the action it searched on first — `createdBy` with
 * `sortBy=dateCreated-desc`, `voidedBy` with `sortBy=dateVoided-desc` — since ordering by the
 * observation's own datetime would bury an obs backdated to last year but entered this morning.
 *
 * <p>Every filter narrows, and naming none matches every observation. `startDate` and `endDate`
 * bound when the audit action happened rather than the observation's own datetime, and run
 * inclusively.
 */
@Controller
public class PihAppsObsRestController {

    protected Log log = LogFactory.getLog(getClass());

    /**
     * The same gate as this package's other administrative endpoints. An observation audit reaches
     * across every patient's record, so it is not something a clinical role should be able to run.
     */
    private static final String REQUIRED_PRIVILEGE = "App: coreapps.systemAdministration";

    @Autowired
    private PihAppsService pihAppsService;

    @RequestMapping(value = "/rest/v1/pihapps/obs", method = RequestMethod.GET)
    @ResponseBody
    public Object searchObs(HttpServletRequest request, HttpServletResponse response,
                            @RequestParam(value = "createdBy", required = false) User createdBy,
                            @RequestParam(value = "voidedBy", required = false) User voidedBy,
                            @RequestParam(value = "startDate", required = false) String startDate,
                            @RequestParam(value = "endDate", required = false) String endDate,
                            @RequestParam(value = "includeVoided", required = false,
                                    defaultValue = "false") boolean includeVoided,
                            @RequestParam(value = "sortBy", required = false) List<String> sortBy)
            throws ResponseException {

        if (!Context.hasPrivilege(REQUIRED_PRIVILEGE)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            Date fromDate;
            Date toDate;
            try {
                fromDate = PihAppsRestSupport.parseBound(startDate, false);
                toDate = PihAppsRestSupport.parseBound(endDate, true);
            }
            catch (Exception e) {
                throw new InvalidSearchException(PihAppsRestSupport.dateFormatMessage("startDate", "endDate"), e);
            }

            if (fromDate != null && toDate != null && fromDate.after(toDate)) {
                throw new InvalidSearchException("startDate must not be after endDate.");
            }

            RequestContext context = RestUtil.getRequestContext(request, response);

            ObsSearchCriteria searchCriteria = new ObsSearchCriteria();
            searchCriteria.setCreatedBy(createdBy);
            searchCriteria.setVoidedBy(voidedBy);
            searchCriteria.setAuditOnOrAfter(fromDate);
            searchCriteria.setAuditOnOrBefore(toDate);
            searchCriteria.setIncludeVoided(includeVoided);
            searchCriteria.setSortCriteria(PihAppsRestSupport.parseSortCriteria(sortBy));
            searchCriteria.setStartIndex(context.getStartIndex());
            searchCriteria.setLimit(context.getLimit());

            ObsSearchResult result = pihAppsService.getObs(searchCriteria);
            Long totalCount = result.getTotalCount();
            boolean hasMore = totalCount > context.getStartIndex() + context.getLimit();

            // AlreadyPaged renders the envelope every other OpenMRS list endpoint returns: results in
            // the requested representation, next and previous links, and totalCount when asked for.
            return new AlreadyPaged<>(context, result.getObs(), hasMore, totalCount).toSimpleObject(null);
        }
        catch (InvalidSearchException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return RestUtil.wrapErrorResponse(e, PihAppsRestSupport.INVALID_SEARCH_REASON);
        }
        catch (Exception e) {
            log.error("Failed to search observations by audit user", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return RestUtil.wrapErrorResponse(e, "Failed to search observations by audit user");
        }
    }

    /**
     * A parameter core's editors could not resolve is still a bad request, so it is answered in the
     * same shape as every other one this endpoint rejects rather than in Spring's own error format.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public SimpleObject handleUnresolvedParameter(MethodArgumentTypeMismatchException e) {
        return PihAppsRestSupport.unresolvedParameterResponse(e);
    }
}
