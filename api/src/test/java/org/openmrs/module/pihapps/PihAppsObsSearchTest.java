package org.openmrs.module.pihapps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Obs;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.SortCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchResult;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/**
 * Covers searching observations by the user who created or voided them. The fixture's creation and
 * voiding dates run in a different order from the observation datetimes, so an implementation that
 * ordered by the wrong column would fail here rather than look plausible.
 */
public class PihAppsObsSearchTest extends BaseModuleContextSensitiveTest {

    private PihAppsService service;

    private User bruno;

    private User butch;

    @BeforeEach
    public void setup() {
        executeDataSet("obsAuditTestDataset.xml");
        service = Context.getService(PihAppsService.class);
        bruno = Context.getUserService().getUser(501);
        butch = Context.getUserService().getUser(502);
    }

    private List<Integer> obsIds(List<Obs> obs) {
        return obs.stream().map(Obs::getObsId).collect(Collectors.toList());
    }

    /**
     * The search under test. Building the criteria in one place means a test cannot silently set
     * the wrong field, and keeps each case reading as the question it is asking.
     */
    private ObsSearchResult search(User createdBy, User voidedBy, Date fromDate, Date toDate, Integer startIndex,
            Integer limit) {
        ObsSearchCriteria searchCriteria = new ObsSearchCriteria();
        searchCriteria.setCreatedBy(createdBy);
        searchCriteria.setVoidedBy(voidedBy);
        // The criteria name the column each range bounds, so the test picks the one the case is
        // about — the same choice a client makes. Each case names at most one action.
        if (voidedBy != null) {
            searchCriteria.setVoidedOnOrAfter(fromDate);
            searchCriteria.setVoidedOnOrBefore(toDate);
        }
        else {
            searchCriteria.setCreatedOnOrAfter(fromDate);
            searchCriteria.setCreatedOnOrBefore(toDate);
        }
        searchCriteria.setIncludeVoided(true);
        searchCriteria.setSortCriteria(auditSortCriteria(voidedBy));
        searchCriteria.setStartIndex(startIndex);
        searchCriteria.setLimit(limit);
        return service.getObs(searchCriteria);
    }

    /**
     * The ordering an audit asks for, which the endpoint sets rather than the service: the audit
     * action the search named, most recent first, with the obs id breaking ties. These tests assert
     * on order, so they have to ask for the same one the endpoint does.
     */
    private List<SortCriteria> auditSortCriteria(User voidedBy) {
        List<SortCriteria> sortCriteria = new ArrayList<>();
        String actionDate = voidedBy != null ? "dateVoided" : "dateCreated";
        sortCriteria.add(new SortCriteria(actionDate, SortCriteria.Direction.DESC));
        sortCriteria.add(new SortCriteria("obsId", SortCriteria.Direction.DESC));
        return sortCriteria;
    }

    private List<Obs> obs(User createdBy, User voidedBy, Date fromDate, Date toDate, Integer startIndex,
            Integer limit) {
        return search(createdBy, voidedBy, fromDate, toDate, startIndex, limit).getObs();
    }

    private Long count(User createdBy, User voidedBy, Date fromDate, Date toDate) {
        return search(createdBy, voidedBy, fromDate, toDate, null, null).getTotalCount();
    }

    /** A moment on a September 2026 day, matching the fixture's audit dates. */
    private Date august(int dayOfMonth, int hourOfDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.AUGUST, dayOfMonth, hourOfDay, 0, 0);
        return calendar.getTime();
    }

    private Date september(int dayOfMonth, int hourOfDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.SEPTEMBER, dayOfMonth, hourOfDay, 0, 0);
        return calendar.getTime();
    }

    @Test
    public void shouldFindObsCreatedByAUserMostRecentlyCreatedFirst() {
        List<Obs> results = obs(bruno, null, null, null, null, null);

        assertThat(obsIds(results), contains(2002, 2001, 2004));
    }

    @Test
    public void shouldIncludeVoidedObsWhenSearchingByCreator() {
        List<Obs> results = obs(bruno, null, null, null, null, null);

        assertThat(results.stream().anyMatch(Obs::getVoided), is(true));
    }

    @Test
    public void shouldFindObsVoidedByAUserMostRecentlyVoidedFirst() {
        List<Obs> results = obs(null, butch, null, null, null, null);

        assertThat(obsIds(results), contains(2005, 2004));
    }

    @Test
    public void shouldNotFindObsVoidedByAnotherUser() {
        assertThat(obs(null, bruno, null, null, null, null), is(java.util.Collections.emptyList()));
    }

    @Test
    public void shouldNarrowByBothUsersWhenBothAreGiven() {
        List<Obs> results = obs(bruno, butch, null, null, null, null);

        assertThat(obsIds(results), contains(2004));
    }

    @Test
    public void shouldPageResults() {
        assertThat(obsIds(obs(bruno, null, null, null, 0, 2)), contains(2002, 2001));
        assertThat(obsIds(obs(bruno, null, null, null, 2, 2)), contains(2004));
        assertThat(obsIds(obs(bruno, null, null, null, 1, 1)), contains(2001));
    }

    @Test
    public void shouldCountTheWholeResultSetRatherThanThePage() {
        assertThat(count(bruno, null, null, null), is(3L));
        assertThat(count(null, butch, null, null), is(2L));
        assertThat(count(bruno, butch, null, null), is(1L));
    }

    @Test
    public void shouldBoundACreatedBySearchByWhenTheObsWasCreated() {
        // obs 2001 was created on 1 Sep, 2002 on 3 Sep, 2004 on 28 Aug
        List<Obs> results = obs(bruno, null, september(1, 0), september(2, 0), null, null);

        assertThat(obsIds(results), contains(2001));
    }

    @Test
    public void shouldBoundACreatedBySearchWithOnlyOneEndGiven() {
        assertThat(obsIds(obs(bruno, null, september(2, 0), null, null, null)), contains(2002));
        assertThat(obsIds(obs(bruno, null, null, september(2, 0), null, null)),
                contains(2001, 2004));
    }

    @Test
    public void shouldBoundAVoidedBySearchByWhenTheObsWasVoided() {
        // obs 2004 was voided on 4 Sep and 2005 on 5 Sep
        List<Obs> results = obs(null, butch, september(5, 0), null, null, null);

        assertThat(obsIds(results), contains(2005));
    }

    /**
     * The fixture's creation dates deliberately run in a different order from its obs datetimes, so
     * a range applied to the wrong column would pick different rows.
     */
    @Test
    public void shouldBoundByTheAuditActionRatherThanTheObsDatetime() {
        // obs 2005 has an obs_datetime of 25 Aug but was created on 20 Aug and voided on 5 Sep
        assertThat(obsIds(obs(butch, null, september(1, 0), null, null, null)), contains(2003));
        assertThat(obsIds(obs(null, butch, september(1, 0), null, null, null)),
                contains(2005, 2004));
    }

    @Test
    public void shouldTreatBothEndsOfTheRangeAsInclusive() {
        // obs 2001 was created at 08:00 on 1 Sep, so a range of exactly that hour includes it
        List<Obs> results = obs(bruno, null, september(1, 8), september(1, 8), null, null);

        assertThat(obsIds(results), contains(2001));
    }

    @Test
    public void shouldCountAndPageWithinTheRange() {
        Date from = september(1, 0);
        assertThat(count(bruno, null, from, null), is(2L));
        assertThat(obsIds(obs(bruno, null, from, null, 0, 1)), contains(2002));
        assertThat(obsIds(obs(bruno, null, from, null, 1, 1)), contains(2001));
    }

    @Test
    public void shouldFindNothingWhenTheRangeExcludesEverything() {
        List<Obs> results = obs(bruno, null, september(20, 0), september(21, 0), null, null);

        assertThat(results, is(java.util.Collections.emptyList()));
        assertThat(count(bruno, null, september(20, 0), september(21, 0)), is(0L));
    }

    /**
     * The audit endpoint asks for voided observations, so the helper above does too. Every other
     * caller gets them left out, which is what this pins down.
     */
    @Test
    public void shouldLeaveOutVoidedObsUnlessAskedFor() {
        // 2004 and 2005 are this fixture's voided observations, both voided by butch
        ObsSearchCriteria searchCriteria = new ObsSearchCriteria();
        searchCriteria.setVoidedBy(butch);

        assertThat(service.getObs(searchCriteria).getObs(), is(java.util.Collections.emptyList()));

        searchCriteria.setIncludeVoided(true);
        assertThat(obsIds(service.getObs(searchCriteria).getObs()), containsInAnyOrder(2004, 2005));
    }

    /**
     * Each range names its own column, so they combine rather than one displacing another and none
     * of them depends on a matching user filter being given.
     */
    @Test
    public void shouldApplyEachRangeToItsOwnColumnIndependently() {
        // a creation range with no createdBy filter still narrows, which the old single range could
        // not express — it was silently ignored
        ObsSearchCriteria creationOnly = new ObsSearchCriteria();
        creationOnly.setIncludeVoided(true);
        creationOnly.setCreatedOnOrAfter(september(20, 0));

        assertThat(service.getObs(creationOnly).getObs(), is(java.util.Collections.emptyList()));

        // 2004 was created 28 Aug and voided 4 Sep; 2005 was voided 5 Sep but created back on 20
        // Aug, so only the creation range tells them apart — the two bound different columns
        ObsSearchCriteria both = new ObsSearchCriteria();
        both.setIncludeVoided(true);
        both.setCreatedOnOrAfter(august(25, 0));
        both.setVoidedOnOrAfter(september(1, 0));

        assertThat(obsIds(service.getObs(both).getObs()), contains(2004));
    }

    @Test
    public void shouldSearchWithoutAnyAuditFilter() {
        // The service places no audit-specific requirement on the criteria, so a search naming none
        // of them is a plain obs search. The audit endpoint asks for at least one itself.
        ObsSearchCriteria searchCriteria = new ObsSearchCriteria();
        searchCriteria.setIncludeVoided(true);

        assertThat(obsIds(service.getObs(searchCriteria).getObs()),
                hasItems(2001, 2002, 2003, 2004, 2005));
    }
}

