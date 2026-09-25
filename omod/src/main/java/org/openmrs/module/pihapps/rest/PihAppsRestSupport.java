package org.openmrs.module.pihapps.rest;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Provider;
import org.openmrs.module.pihapps.SortCriteria;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.response.InvalidSearchException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The parts the search endpoints in this package have in common: reading a date bound off a request
 * parameter, and wording a rejected search the same way. Kept together so that they cannot drift
 * apart on what a date-only upper bound means, or on how a bad parameter reads to a client.
 */
final class PihAppsRestSupport {

    /**
     * The context a rejected search is reported under. `RestUtil.wrapErrorResponse` prints it ahead
     * of the exception's own message, so it says what kind of failure this is and leaves the
     * particulars to the message.
     */
    static final String INVALID_SEARCH_REASON = "Invalid search parameters";

    private PihAppsRestSupport() {
    }

    /**
     * Reads a date parameter with no bound semantics, for an endpoint that decides what an end of a
     * range means further down. Blank means the parameter was not given.
     *
     * @param value the parameter as it arrived, or null or blank for no date
     * @return the date, or null if none was given
     */
    static Date parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return (Date) ConversionUtil.convert(value.trim(), Date.class);
    }

    /** The format message for an endpoint with more date parameters than are worth listing. */
    static String dateFormatMessage() {
        return "Date parameters must be ISO 8601, e.g. 2026-09-01 or 2026-09-01T13:45:00.000+0000";
    }

    /**
     * Reads the `sortBy` parameter, in the `field-direction` form this module's other endpoints
     * accept: `encounterDatetime-desc`, or just `encounterDatetime` for ascending. Several may be
     * given, and they order the results outermost first.
     *
     * <p>Ordering is the caller's to choose, and a page without one is not deterministic — two
     * requests for the same page can repeat or skip a row — so a client that pages should always
     * name one, ending in something unique such as the primary key.
     *
     * @param sortBy the parameter as it arrived, or null or empty for no ordering
     * @return the criteria in the order given, empty if none were named
     * @throws InvalidSearchException if a direction is neither asc nor desc
     */
    static List<SortCriteria> parseSortCriteria(List<String> sortBy) {
        List<SortCriteria> sortCriteria = new ArrayList<>();
        if (sortBy == null) {
            return sortCriteria;
        }
        for (String value : sortBy) {
            if (StringUtils.isBlank(value)) {
                continue;
            }
            String[] components = value.trim().split("-", 2);
            SortCriteria.Direction direction = SortCriteria.Direction.ASC;
            if (components.length > 1) {
                try {
                    direction = SortCriteria.Direction.valueOf(components[1].toUpperCase());
                }
                catch (IllegalArgumentException e) {
                    throw new InvalidSearchException(
                            "sortBy direction must be asc or desc, e.g. encounterDatetime-desc: " + value, e);
                }
            }
            sortCriteria.add(new SortCriteria(components[0], direction));
        }
        return sortCriteria;
    }

    /**
     * Answers a parameter core's property editors could not resolve into the object it names. Kept
     * here so that both endpoints word the same failure the same way, and so that a bad `createdBy`
     * reads the same to a client whichever one it asked.
     */
    static SimpleObject unresolvedParameterResponse(MethodArgumentTypeMismatchException e) {
        String noun = Provider.class.equals(e.getRequiredType()) ? "provider" : "user";
        return RestUtil.wrapErrorResponse(new InvalidSearchException("No " + noun + " found with id: " + e.getValue()),
                INVALID_SEARCH_REASON);
    }
}
