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
     * Bound the audit action rather than the observation's own datetime, which {@link #onOrAfter}
     * and {@link #onOrBefore} cover. An observation backdated to last year but entered this morning
     * was modified this morning, which is what a search over a timeframe is asking about.
     *
     * <p>Each user filter is bounded by the column belonging to its action, so naming both users
     * and a range asks for observations that one user created and the other voided, each within the
     * window. Both ends run inclusively and are applied as given, so a caller that means a whole
     * day passes that day's last moment.
     */
    private Date auditOnOrAfter;

    /** @see #auditOnOrAfter */
    private Date auditOnOrBefore;

    private List<SortCriteria> sortCriteria;
    private Integer startIndex;
    private Integer limit;
}
