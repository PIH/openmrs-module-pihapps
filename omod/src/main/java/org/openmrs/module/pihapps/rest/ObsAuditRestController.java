package org.openmrs.module.pihapps.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.SortCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchResult;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.representation.CustomRepresentation;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Searches observations by the user who created them or the user who voided them, which the core
 * REST API cannot do: neither the obs resource, the observation search handler nor core's own
 * {@link org.openmrs.parameter.ObsSearchCriteria} has a creator or voidedBy field, so those columns
 * can be read off an observation but not searched on.
 *
 * <p>Results are paged and ordered most recent action first, and each observation is rendered in the
 * standard obs representation, so a client can ask for whatever it needs with `v`:
 *
 * <pre>
 * GET /openmrs/ws/rest/v1/pihapps/obs?createdBy=&lt;uuid&gt;&amp;limit=20&amp;totalCount=true
 * GET /openmrs/ws/rest/v1/pihapps/obs?voidedBy=cd8a4b8e-...&amp;v=custom:(uuid,concept:(display),auditInfo)
 * GET /openmrs/ws/rest/v1/pihapps/obs?createdBy=&lt;uuid&gt;&amp;startDate=2026-09-01&amp;endDate=2026-09-30
 * </pre>
 *
 * <p>`createdBy` and `voidedBy` are bound by core's property editors, so each takes a uuid or a
 * primary key.
 *
 * <p>`startDate` and `endDate` bound when the audit action happened rather than the observation's
 * own datetime, and run inclusively.
 */
@Controller
public class ObsAuditRestController {

    protected Log log = LogFactory.getLog(getClass());

    /**
     * The same gate as this package's other administrative endpoints. An observation audit reaches
     * across every patient's record, so it is not something a clinical role should be able to run.
     */
    private static final String REQUIRED_PRIVILEGE = "App: coreapps.systemAdministration";

    /**
     * Enough to say what was recorded and who touched it. `auditInfo` is what makes this an audit
     * result: it carries the creator and the voiding user with their timestamps.
     */
    private static final String DEFAULT_REPRESENTATION =
            "(uuid,display,obsDatetime,voided,concept:(uuid,display),person:(uuid,display)," +
                    "encounter:(uuid),value:ref,comment,auditInfo)";

    @Autowired
    private PihAppsService pihAppsService;

    @RequestMapping(value = "/rest/v1/pihapps/obs", method = RequestMethod.GET)
    @ResponseBody
    public Object searchObs(HttpServletRequest request, HttpServletResponse response,
                            @RequestParam(value = "createdBy", required = false) User createdBy,
                            @RequestParam(value = "voidedBy", required = false) User voidedBy,
                            @RequestParam(value = "startDate", required = false) String startDate,
                            @RequestParam(value = "endDate", required = false) String endDate)
            throws ResponseException {

        if (!Context.hasPrivilege(REQUIRED_PRIVILEGE)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            if (createdBy == null && voidedBy == null) {
                throw new InvalidSearchException("Please specify createdBy, voidedBy, or both.");
            }

            Date fromDate;
            Date toDate;
            try {
                fromDate = AuditRestSupport.parseBound(startDate, false);
                toDate = AuditRestSupport.parseBound(endDate, true);
            }
            catch (Exception e) {
                throw new InvalidSearchException(AuditRestSupport.dateFormatMessage(), e);
            }

            if (fromDate != null && toDate != null && fromDate.after(toDate)) {
                throw new InvalidSearchException("startDate must not be after endDate.");
            }

            RequestContext context = RestUtil.getRequestContext(request, response,
                    new CustomRepresentation(DEFAULT_REPRESENTATION));

            ObsSearchCriteria searchCriteria = new ObsSearchCriteria();
            searchCriteria.setCreatedBy(createdBy);
            searchCriteria.setVoidedBy(voidedBy);
            searchCriteria.setAuditOnOrAfter(fromDate);
            searchCriteria.setAuditOnOrBefore(toDate);
            // A voidedBy search would return nothing with the voided rows filtered out, and an
            // auditor looking at what a user created wants to see what has since been deleted just
            // as much as what survives, so an audit always asks for them.
            searchCriteria.setIncludeVoided(true);
            searchCriteria.setSortCriteria(auditSortCriteria(voidedBy));
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
            return RestUtil.wrapErrorResponse(e, AuditRestSupport.INVALID_SEARCH_REASON);
        }
        catch (Exception e) {
            log.error("Failed to search observations by audit user", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return RestUtil.wrapErrorResponse(e, "Failed to search observations by audit user");
        }
    }

    /**
     * "Most recent first" means the most recent audit action: when the row was created for a
     * createdBy search, when it was voided for a voidedBy search. Ordering by the observation's own
     * datetime would bury an obs backdated to last year but entered this morning, which is the
     * opposite of what an audit needs. The obs id breaks ties so that paging cannot repeat or skip
     * a row when several share a timestamp.
     */
    private List<SortCriteria> auditSortCriteria(User voidedBy) {
        List<SortCriteria> sortCriteria = new ArrayList<>();
        String actionDate = voidedBy != null ? "dateVoided" : "dateCreated";
        sortCriteria.add(new SortCriteria(actionDate, SortCriteria.Direction.DESC));
        sortCriteria.add(new SortCriteria("obsId", SortCriteria.Direction.DESC));
        return sortCriteria;
    }

    /**
     * A parameter core's editors could not resolve is still a bad request, so it is answered in the
     * same shape as every other one this endpoint rejects rather than in Spring's own error format.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public SimpleObject handleUnresolvedParameter(MethodArgumentTypeMismatchException e) {
        return AuditRestSupport.unresolvedParameterResponse(e);
    }
}
