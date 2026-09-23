package org.openmrs.module.pihapps.rest;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Provider;
import org.openmrs.module.pihapps.SortCriteria;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.response.InvalidSearchException;
import org.openmrs.util.OpenmrsUtil;
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
     * Reads one end of a date range, in any of the formats the REST API accepts elsewhere.
     *
     * <p>A bare date names the whole of that day: an upper bound of `2026-09-30` means through the end of the
     * 30th, not its first instant, since a range given in dates is asking about days. Give a time
     * to bound the range to the second instead.
     *
     * @param value the parameter as it arrived, or null or blank for no bound
     * @param isUpperBound whether a date without a time should be stretched to the end of the day
     * @return the bound, or null if none was given
     */
    static Date parseBound(String value, boolean isUpperBound) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        String trimmed = value.trim();
        Date date = (Date) ConversionUtil.convert(trimmed, Date.class);
        return isUpperBound && isDateOnly(trimmed) ? OpenmrsUtil.getLastMomentOfDay(date) : date;
    }

    private static boolean isDateOnly(String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /**
     * @param fromParam what the endpoint calls its lower bound
     * @param toParam what the endpoint calls its upper bound
     */
    static String dateFormatMessage(String fromParam, String toParam) {
        return fromParam + " and " + toParam + " must be ISO 8601, e.g. 2026-09-01 or 2026-09-01T13:45:00.000+0000";
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
