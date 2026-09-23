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
import java.util.Date;
import java.util.List;

/**
 * Searches encounters by the user who created, changed or voided them, and by the provider recorded
 * on them — none of which the core REST API can do. Core's `EncounterSearchCriteria` carries a
 * providers field but no search handler exposes it, and it has no creator, changedBy or voidedBy
 * field at all, so those columns can be read off an encounter but not searched on.
 *
 * <p>Results are paged and ordered as `sortBy` asks, and each encounter is rendered in the standard
 * encounter representation, so a client can ask for whatever it needs with `v`:
 *
 * <pre>
 * GET /openmrs/ws/rest/v1/pihapps/encounter?createdBy=&lt;uuid&gt;&amp;limit=20&amp;totalCount=true
 * GET /openmrs/ws/rest/v1/pihapps/encounter?changedBy=&lt;uuid&gt;&amp;auditOnOrAfter=2026-09-01&amp;auditOnOrBefore=2026-09-30
 * GET /openmrs/ws/rest/v1/pihapps/encounter?provider=&lt;uuid&gt;&amp;v=custom:(uuid,encounterDatetime,auditInfo)
 * GET /openmrs/ws/rest/v1/pihapps/encounter?provider=&lt;uuid&gt;&amp;encounterType=&lt;uuid&gt;
 * GET /openmrs/ws/rest/v1/pihapps/encounter?voidedBy=&lt;uuid&gt;&amp;includeVoided=true
 * GET /openmrs/ws/rest/v1/pihapps/encounter?provider=&lt;uuid&gt;&amp;sortBy=encounterDatetime-desc&amp;sortBy=encounterId-desc
 * </pre>
 *
 * <p>`createdBy`, `changedBy`, `voidedBy` and `provider` are bound by core's property editors, so
 * each takes a uuid or a primary key. `encounterType` takes a uuid or its name.
 *
 * <p>`includeVoided` decides whether voided encounters come back alongside the surviving ones, and
 * is off unless asked for. An audit normally wants them on: a `voidedBy` search returns nothing
 * without them, and an auditor looking at what a user entered wants to see what has since been
 * deleted just as much as what survives. Callers tell the two apart by each encounter's voided
 * flag.
 *
 * <p>`sortBy` takes `field-direction`, or just `field` for ascending, and may be given several
 * times to order by more than one. Nothing is sorted unless asked, and a page without an ordering
 * is not deterministic, so a client that pages should name one ending in something unique such as
 * `encounterId`. An audit wants the action it searched on first — `createdBy` with
 * `sortBy=dateCreated-desc`, a provider search with `sortBy=encounterDatetime-desc` — since
 * ordering by anything else would bury an encounter backdated to last year but entered this
 * morning.
 *
 * <p>Every filter narrows, so naming several asks for the encounters satisfying all of them, and
 * naming none matches every encounter. `auditOnOrAfter` and `auditOnOrBefore` bound whichever
 * column the search is about: the named audit action, or the encounter's own datetime where only a
 * provider was named. They run inclusively, and a bare date names the whole of that day.
 */
@Controller
public class PihAppsEncounterRestController {

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
                                   @RequestParam(value = "auditOnOrAfter", required = false) String auditOnOrAfter,
                                   @RequestParam(value = "auditOnOrBefore", required = false) String auditOnOrBefore,
                                   @RequestParam(value = "includeVoided", required = false,
                                           defaultValue = "false") boolean includeVoided,
                                   @RequestParam(value = "sortBy", required = false) List<String> sortBy)
            throws ResponseException {

        if (!Context.hasPrivilege(REQUIRED_PRIVILEGE)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
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
                fromDate = PihAppsRestSupport.parseBound(auditOnOrAfter, false);
                toDate = PihAppsRestSupport.parseBound(auditOnOrBefore, true);
            }
            catch (Exception e) {
                throw new InvalidSearchException(PihAppsRestSupport.dateFormatMessage("auditOnOrAfter", "auditOnOrBefore"), e);
            }

            if (fromDate != null && toDate != null && fromDate.after(toDate)) {
                throw new InvalidSearchException("auditOnOrAfter must not be after auditOnOrBefore.");
            }

            RequestContext context = RestUtil.getRequestContext(request, response,
                    new CustomRepresentation(DEFAULT_REPRESENTATION));

            EncounterSearchCriteria searchCriteria = new EncounterSearchCriteria();
            searchCriteria.setCreatedBy(createdBy);
            searchCriteria.setChangedBy(changedBy);
            searchCriteria.setVoidedBy(voidedBy);
            searchCriteria.setProvider(provider);
            searchCriteria.setEncounterType(type);
            searchCriteria.setIncludeVoided(includeVoided);
            searchCriteria.setAuditOnOrAfter(fromDate);
            searchCriteria.setAuditOnOrBefore(toDate);
            searchCriteria.setSortCriteria(PihAppsRestSupport.parseSortCriteria(sortBy));
            searchCriteria.setStartIndex(context.getStartIndex());
            searchCriteria.setLimit(context.getLimit());

            EncounterSearchResult result = pihAppsService.getEncounters(searchCriteria);
            Long totalCount = result.getTotalCount();
            boolean hasMore = totalCount > context.getStartIndex() + context.getLimit();

            return new AlreadyPaged<>(context, result.getEncounters(), hasMore, totalCount).toSimpleObject(null);
        }
        catch (InvalidSearchException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return RestUtil.wrapErrorResponse(e, PihAppsRestSupport.INVALID_SEARCH_REASON);
        }
        catch (Exception e) {
            log.error("Failed to search encounters by audit user", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return RestUtil.wrapErrorResponse(e, "Failed to search encounters by audit user");
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
