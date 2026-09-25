package org.openmrs.module.pihapps.obs;

import lombok.Data;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.module.pihapps.SortCriteria;

import java.util.Date;
import java.util.List;

@Data
public class ObsSearchCriteria {
    private Patient patient;
    private List<Concept> concepts;
    private Date onOrBefore;
    private Date onOrAfter;

    /**
     * Whether voided observations are returned alongside the surviving ones. Off by default, since
     * a search is normally asking what a record says now, and callers can tell the two apart by
     * each observation's voided flag.
     *
     * <p>An audit search turns this on: voided observations are the whole point of a
     * {@link #voidedBy} search, and what an auditor most wants to see in a {@link #createdBy} one.
     */
    private boolean includeVoided = false;

    /** Restrict to observations this user created. */
    private User createdBy;

    /** Restrict to observations this user voided. See {@link #createdBy}. */
    private User voidedBy;

    /**
     * Bound when the observation was created. Each of the ranges below names the column it applies
     * to, and each is independent of the filters: `createdOnOrAfter` narrows by creation date
     * whether or not {@link #createdBy} is also given, and naming several asks for all of them.
     *
     * <p>Which range a search wants is the caller's to decide. An audit bounds the action it is
     * about rather than the observation's own datetime — an observation backdated to last year but
     * entered this morning was entered this morning — while {@link #onOrAfter} and
     * {@link #onOrBefore} bound obsDatetime, when the observation says it was taken.
     *
     * <p>Both ends of every range run inclusively. An upper end carrying no time of day is read as
     * the whole of that day.
     */
    private Date createdOnOrAfter;

    /** @see #createdOnOrAfter */
    private Date createdOnOrBefore;

    /** Bound when the observation was voided. @see #createdOnOrAfter */
    private Date voidedOnOrAfter;

    /** @see #createdOnOrAfter */
    private Date voidedOnOrBefore;

    private List<SortCriteria> sortCriteria;
    private Integer startIndex;
    private Integer limit;
}
