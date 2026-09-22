package org.openmrs.module.pihapps.encounter;

import lombok.Data;
import org.openmrs.EncounterType;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.module.pihapps.SortCriteria;

import java.util.Date;
import java.util.List;

/**
 * Describes an encounter search. Core's own {@link org.openmrs.parameter.EncounterSearchCriteria}
 * carries a providers field that no search handler exposes, and has no creator, changedBy or
 * voidedBy field at all, which is why this exists rather than the module reusing it.
 *
 * <p>Every filter narrows, so naming several asks for the encounters satisfying all of them, and
 * naming none matches every encounter.
 */
@Data
public class EncounterSearchCriteria {

    /**
     * Whether voided encounters are returned alongside the surviving ones. Off by default, since a
     * search is normally asking what a record says now, and callers can tell the two apart by each
     * encounter's voided flag.
     *
     * <p>An audit search turns this on: voided encounters are the whole point of a
     * {@link #voidedBy} search, and what an auditor most wants to see in the others.
     */
    private boolean includeVoided = false;

    /** Restrict to encounters this user created. */
    private User createdBy;

    /** Restrict to encounters this user changed. */
    private User changedBy;

    /** Restrict to encounters this user voided. */
    private User voidedBy;

    /** Restrict to encounters this provider is recorded on. */
    private Provider provider;

    /** Restrict to encounters of this type. */
    private EncounterType encounterType;

    /**
     * Bound whatever the search is about. Where an audit action is named these bound that action's
     * column — an encounter backdated to last year but entered this morning was entered this
     * morning, and core's own encounter search already covers encounterDatetime for the cases it
     * can reach. A provider search names no action, so there they bound the encounter's own
     * datetime, which is both what a provider's caseload is asked about and something core cannot
     * filter by provider.
     *
     * <p>Both ends run inclusively and are applied as given, so a caller that means a whole day
     * passes that day's last moment.
     */
    private Date auditOnOrAfter;

    /** @see #auditOnOrAfter */
    private Date auditOnOrBefore;

    private List<SortCriteria> sortCriteria;
    private Integer startIndex;
    private Integer limit;
}
