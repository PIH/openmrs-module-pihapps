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
     * Bound when the encounter was created. Each of the four ranges below names the column it
     * applies to, and each is independent of the filters: `createdOnOrAfter` narrows by creation
     * date whether or not `createdBy` is also given, and naming several asks for all of them.
     *
     * <p>Which range a search wants is the caller's to decide. An audit of what a user entered
     * wants the range against that user's action — an encounter backdated to last year but entered
     * this morning was entered this morning — while a provider's caseload is asked about by
     * {@link #encounterDatetimeOnOrAfter}, when the encounters actually happened.
     *
     * <p>Both ends of every range run inclusively and are applied as given, so a caller that means
     * a whole day passes that day's last moment.
     */
    private Date createdOnOrAfter;

    /** @see #createdOnOrAfter */
    private Date createdOnOrBefore;

    /** Bound when the encounter was last changed. @see #createdOnOrAfter */
    private Date changedOnOrAfter;

    /** @see #createdOnOrAfter */
    private Date changedOnOrBefore;

    /** Bound when the encounter was voided. @see #createdOnOrAfter */
    private Date voidedOnOrAfter;

    /** @see #createdOnOrAfter */
    private Date voidedOnOrBefore;

    /** Bound the encounter's own datetime — when it happened. @see #createdOnOrAfter */
    private Date encounterDatetimeOnOrAfter;

    /** @see #createdOnOrAfter */
    private Date encounterDatetimeOnOrBefore;

    private List<SortCriteria> sortCriteria;
    private Integer startIndex;
    private Integer limit;
}
