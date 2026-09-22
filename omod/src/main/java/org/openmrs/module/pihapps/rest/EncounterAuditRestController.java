package org.openmrs.module.pihapps.rest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.EncounterType;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.SortCriteria;
import org.openmrs.module.pihapps.encounter.EncounterSearchCriteria;
import org.openmrs.module.pihapps.encounter.EncounterSearchResult;
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
 * Searches encounters by the user who created, changed or voided them, and by the provider recorded
 * on them — none of which the core REST API can do. Core's `EncounterSearchCriteria` carries a
 * providers field but no search handler exposes it, and it has no creator, changedBy or voidedBy
 * field at all, so those columns can be read off an encounter but not searched on.
 *
 * <p>Results are paged and ordered by the most recent of the audit actions the search named, and
 * each encounter is rendered in the standard encounter representation, so a client can ask for
 * whatever it needs with `v`:
 *
 * <pre>
 * GET /openmrs/ws/rest/v1/pihapps/encounter?createdBy=&lt;uuid&gt;&amp;limit=20&amp;totalCount=true
 * GET /openmrs/ws/rest/v1/pihapps/encounter?changedBy=&lt;uuid&gt;&amp;startDate=2026-09-01&amp;endDate=2026-09-30
 * GET /openmrs/ws/rest/v1/pihapps/encounter?provider=&lt;uuid&gt;&amp;v=custom:(uuid,encounterDatetime,auditInfo)
 * GET /openmrs/ws/rest/v1/pihapps/encounter?provider=&lt;uuid&gt;&amp;encounterType=&lt;uuid&gt;
 * </pre>
 *
 * <p>`createdBy`, `changedBy`, `voidedBy` and `provider` are bound by core's property editors, so
 * each takes a uuid or a primary key. `encounterType` takes a uuid or its name.
 *
 * <p>Every filter narrows, so naming several asks for the encounters satisfying all of them. At
 * least one user or provider is required; `encounterType` narrows an audit but is not an audit of
 * anything on its own. `startDate` and `endDate` bound when the audit action
 * happened rather than the encounter's own datetime, which is what core's encounter search already
 * covers; they run inclusively, and a bare date names the whole of that day.
 */
@Controller
public class EncounterAuditRestController {

    protected Log log = LogFactory.getLog(getClass());

    /**
     * The same gate as this package's other administrative endpoints. An encounter audit reaches
     * across every patient's record, so it is not something a clinical role should be able to run.
     */
    private static final String REQUIRED_PRIVILEGE = "App: coreapps.systemAdministration";

    /**
     * Enough to say what the encounter was and who touched it. `auditInfo` is what makes this an
     * audit result: it carries the creating, changing and voiding users with their timestamps.
     */
    private static final String DEFAULT_REPRESENTATION =
            "(uuid,display,encounterDatetime,voided,encounterType:(uuid,display),form:(uuid,display)," +
                    "location:(uuid,display),patient:(uuid,display)," +
                    "encounterProviders:(uuid,voided,provider:(uuid,display),encounterRole:(uuid,display)),auditInfo)";

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private PihAppsService pihAppsService;

    @RequestMapping(value = "/rest/v1/pihapps/encounter", method = RequestMethod.GET)
    @ResponseBody
    public Object searchEncounters(HttpServletRequest request, HttpServletResponse response,
                                   @RequestParam(value = "createdBy", required = false) User createdBy,
                                   @RequestParam(value = "changedBy", required = false) User changedBy,
                                   @RequestParam(value = "voidedBy", required = false) User voidedBy,
                                   @RequestParam(value = "provider", required = false) Provider provider,
                                   @RequestParam(value = "encounterType", required = false) String encounterType,
                                   @RequestParam(value = "startDate", required = false) String startDate,
                                   @RequestParam(value = "endDate", required = false) String endDate)
            throws ResponseException {

        if (!Context.hasPrivilege(REQUIRED_PRIVILEGE)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            if (createdBy == null && changedBy == null && voidedBy == null && provider == null) {
                throw new InvalidSearchException(
                        "Please specify at least one of createdBy, changedBy, voidedBy or provider.");
            }

            EncounterType type = null;
            if (StringUtils.isNotBlank(encounterType)) {
                type = getEncounterType(encounterType);
                if (type == null) {
                    throw new InvalidSearchException("No encounter type found with id: " + encounterType);
                }
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

            EncounterSearchCriteria searchCriteria = new EncounterSearchCriteria();
            searchCriteria.setCreatedBy(createdBy);
            searchCriteria.setChangedBy(changedBy);
            searchCriteria.setVoidedBy(voidedBy);
            searchCriteria.setProvider(provider);
            searchCriteria.setEncounterType(type);
            // A voidedBy search would return nothing with the voided rows filtered out, and an
            // auditor looking at what a user entered wants to see what has since been deleted just
            // as much as what survives, so an audit always asks for them.
            searchCriteria.setIncludeVoided(true);
            searchCriteria.setAuditOnOrAfter(fromDate);
            searchCriteria.setAuditOnOrBefore(toDate);
            searchCriteria.setSortCriteria(auditSortCriteria(createdBy, changedBy, voidedBy));
            searchCriteria.setStartIndex(context.getStartIndex());
            searchCriteria.setLimit(context.getLimit());

            EncounterSearchResult result = pihAppsService.getEncounters(searchCriteria);
            Long totalCount = result.getTotalCount();
            boolean hasMore = totalCount > context.getStartIndex() + context.getLimit();

            return new AlreadyPaged<>(context, result.getEncounters(), hasMore, totalCount).toSimpleObject(null);
        }
        catch (InvalidSearchException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return RestUtil.wrapErrorResponse(e, AuditRestSupport.INVALID_SEARCH_REASON);
        }
        catch (Exception e) {
            log.error("Failed to search encounters by audit user", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return RestUtil.wrapErrorResponse(e, "Failed to search encounters by audit user");
        }
    }

    /**
     * Orders by the most recent of the audit actions the search named, so that "most recent first"
     * means the most recent thing the search is about, and matches whichever column the date bounds
     * were applied to. A provider search names no action, so it orders by the encounter's own
     * datetime. The encounter id breaks ties so that paging cannot repeat or skip a row when
     * several share a timestamp.
     */
    private List<SortCriteria> auditSortCriteria(User createdBy, User changedBy, User voidedBy) {
        List<SortCriteria> sortCriteria = new ArrayList<>();
        sortCriteria.add(new SortCriteria(auditSortProperty(createdBy, changedBy, voidedBy),
                SortCriteria.Direction.DESC));
        sortCriteria.add(new SortCriteria("encounterId", SortCriteria.Direction.DESC));
        return sortCriteria;
    }

    /** The named audit action, taking the most recent kind of action when a search named several. */
    private String auditSortProperty(User createdBy, User changedBy, User voidedBy) {
        if (voidedBy != null) {
            return "dateVoided";
        }
        if (changedBy != null) {
            return "dateChanged";
        }
        if (createdBy != null) {
            return "dateCreated";
        }
        return "encounterDatetime";
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

    /**
     * There is no EncounterType Property Editor registered with core, so convert from a String
     * uuid or name to an EncounterType.
     */
    EncounterType getEncounterType(String uuidOrName) {
        EncounterType encounterType = encounterService.getEncounterTypeByUuid(uuidOrName);
        if (encounterType == null) {
            encounterType = encounterService.getEncounterType(uuidOrName);
        }
        return encounterType;
    }
}
