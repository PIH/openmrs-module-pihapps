package org.openmrs.module.pihapps;

import org.junit.jupiter.api.Test;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.module.pihapps.orders.PatientWithOrders;
import org.openmrs.module.pihapps.orders.PatientWithOrdersSearchResult;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class PihAppsServiceTest extends BaseModuleContextSensitiveTest {

    private static final Logger log = LoggerFactory.getLogger(PihAppsServiceTest.class);

    @Autowired
    @Qualifier("pihappsService")
    private PihAppsService pihAppsService;

    @Autowired
    LabOrderConfig labOrderConfig;

    @Test
    public void shouldGetPagedLabOrders() {
        OrderSearchCriteria searchCriteria = new OrderSearchCriteria();
        searchCriteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));
        OrderSearchResult result = pihAppsService.getOrders(searchCriteria);
        assertThat(result.getTotalCount(), equalTo(3L));
        assertThat(result.getOrders().size(), equalTo(3));
        printResult(result);
        searchCriteria.setStartIndex(0);
        searchCriteria.setLimit(2);
        result = pihAppsService.getOrders(searchCriteria);
        assertThat(result.getTotalCount(), equalTo(3L));
        assertThat(result.getOrders().size(), equalTo(2));
        searchCriteria.setStartIndex(2);
        result = pihAppsService.getOrders(searchCriteria);
        assertThat(result.getTotalCount(), equalTo(3L));
        assertThat(result.getOrders().size(), equalTo(1));
    }

    @Test
    public void getPatientsWithOrders_shouldGroupOrdersByPatient() {
        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setStartIndex(0);
        criteria.setLimit(100);

        PatientWithOrdersSearchResult result = pihAppsService.getPatientsWithOrders(criteria);

        for (PatientWithOrders pw : result.getPatients()) {
            assertThat(pw.getPatient(), notNullValue());
            assertThat(pw.getOrders(), not(empty()));
            Patient expectedPatient = pw.getPatient();
            for (Order order : pw.getOrders()) {
                assertThat(order.getPatient(), equalTo(expectedPatient));
            }
        }
    }

    @Test
    public void getPatientsWithOrders_shouldRespectPaginationLimit() {
        OrderSearchCriteria allCriteria = new OrderSearchCriteria();
        allCriteria.setStartIndex(0);
        allCriteria.setLimit(100);
        PatientWithOrdersSearchResult allResults = pihAppsService.getPatientsWithOrders(allCriteria);

        if (allResults.getTotalCount() >= 2) {
            OrderSearchCriteria pageCriteria = new OrderSearchCriteria();
            pageCriteria.setStartIndex(0);
            pageCriteria.setLimit(1);
            PatientWithOrdersSearchResult page1 = pihAppsService.getPatientsWithOrders(pageCriteria);

            assertThat(page1.getPatients().size(), equalTo(1));
            assertThat(page1.getTotalCount(), equalTo(allResults.getTotalCount()));
        }
    }

    @Test
    public void getPatientsWithOrders_shouldOrderStatPatientsBeforeRoutinePatients() throws Exception {
        executeDataSet("pihapps_lab_order_test_data.xml");

        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));
        criteria.setStartIndex(0);
        criteria.setLimit(10);

        PatientWithOrdersSearchResult result = pihAppsService.getPatientsWithOrders(criteria);

        // Dataset has patient 7 (STAT order) and patient 2 (only ROUTINE orders)
        List<PatientWithOrders> patients = result.getPatients();
        assertThat(patients.size(), greaterThan(1));

        // Patient 7 has STAT → must appear before patient 2 (ROUTINE only)
        int patient7Index = -1, patient2Index = -1;
        for (int i = 0; i < patients.size(); i++) {
            int patientId = patients.get(i).getPatient().getId();
            if (patientId == 7) patient7Index = i;
            if (patientId == 2) patient2Index = i;
        }
        assertThat("patient 7 (has STAT order) should be found", patient7Index, greaterThan(-1));
        assertThat("patient 2 (ROUTINE only) should be found", patient2Index, greaterThan(-1));
        assertThat("patient with STAT order should sort before ROUTINE-only patient", patient7Index, greaterThan(-1));
        assertThat(patient7Index, equalTo(0));
        assertThat(patient2Index, equalTo(1));
    }

    @Test
    public void getPatientsWithOrders_shouldRespectCustomSortCriteria() throws Exception {
        executeDataSet("pihapps_lab_order_test_data.xml");

        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));
        criteria.setStartIndex(0);
        criteria.setLimit(10);

        // Default sort (urgency DESC, dateActivated ASC): patient 7 first — has STAT order
        PatientWithOrdersSearchResult defaultResult = pihAppsService.getPatientsWithOrders(criteria);
        assertThat(defaultResult.getPatients().get(0).getPatient().getId(), equalTo(7));

        // Custom sort (dateActivated ASC only): patient 2 first — earliest order 2007-12-09 vs patient 7's 2009-01-15
        criteria.setSortCriteria(Collections.singletonList(
                new SortCriteria("dateActivated", SortCriteria.Direction.ASC)));
        PatientWithOrdersSearchResult customResult = pihAppsService.getPatientsWithOrders(criteria);
        assertThat(customResult.getPatients().get(0).getPatient().getId(), equalTo(2));
    }

    @Test
    public void getPatientsWithOrders_shouldReturnDifferentPatientsOnDifferentPages() throws Exception {
        executeDataSet("pihapps_lab_order_test_data.xml");

        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));

        // Confirm we have exactly 2 distinct patients
        criteria.setStartIndex(0);
        criteria.setLimit(100);
        assertThat(pihAppsService.getPatientsWithOrders(criteria).getTotalCount(), equalTo(2L));

        // Page 1
        criteria.setStartIndex(0);
        criteria.setLimit(1);
        PatientWithOrdersSearchResult page1 = pihAppsService.getPatientsWithOrders(criteria);
        assertThat(page1.getPatients().size(), equalTo(1));
        assertThat(page1.getTotalCount(), equalTo(2L));

        // Page 2
        criteria.setStartIndex(1);
        criteria.setLimit(1);
        PatientWithOrdersSearchResult page2 = pihAppsService.getPatientsWithOrders(criteria);
        assertThat(page2.getPatients().size(), equalTo(1));
        assertThat(page2.getTotalCount(), equalTo(2L));

        // Different patients on different pages
        int page1PatientId = page1.getPatients().get(0).getPatient().getId();
        int page2PatientId = page2.getPatients().get(0).getPatient().getId();
        assertThat("pages should return different patients", page1PatientId, not(equalTo(page2PatientId)));
    }

    @Test
    public void getOrders_shouldFilterByInFulfillmentStatus() throws Exception {
        executeDataSet("pihapps_lab_order_test_data.xml");

        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));
        applyFulfillmentStatus(criteria, OrderFulfillmentStatus.IN_FULFILLMENT);

        OrderSearchResult result = pihAppsService.getOrders(criteria);

        // Only order 102 (IN_PROGRESS) should match; standard dataset has none with IN_PROGRESS
        assertThat(result.getTotalCount(), equalTo(1L));
        assertThat(result.getOrders().get(0).getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
    }

    @Test
    public void getOrders_shouldFilterByAwaitingFulfillmentStatus() {
        // Standard dataset order 7: patient 2, RECEIVED, active → AWAITING_FULFILLMENT
        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));
        applyFulfillmentStatus(criteria, OrderFulfillmentStatus.AWAITING_FULFILLMENT);

        OrderSearchResult result = pihAppsService.getOrders(criteria);

        assertThat(result.getTotalCount(), greaterThan(0L));
        for (Order order : result.getOrders()) {
            // All returned orders must have RECEIVED or null fulfiller status and be active
            boolean isReceived = order.getFulfillerStatus() == Order.FulfillerStatus.RECEIVED;
            boolean isNull = order.getFulfillerStatus() == null;
            assertThat("AWAITING orders must have RECEIVED or null fulfiller status", isReceived || isNull, equalTo(true));
            assertThat("AWAITING orders must not be stopped", order.getDateStopped(), equalTo(null));
        }
    }

    @Test
    public void getOrders_shouldFilterByCompletedFulfillmentStatus() {
        // Standard dataset order 6: patient 2, COMPLETED
        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setOrderTypes(Collections.singletonList(labOrderConfig.getLabTestOrderType()));
        applyFulfillmentStatus(criteria, OrderFulfillmentStatus.COMPLETED_FULFILLMENT);

        OrderSearchResult result = pihAppsService.getOrders(criteria);

        assertThat(result.getTotalCount(), greaterThan(0L));
        for (Order order : result.getOrders()) {
            assertThat(order.getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
        }
    }

    private void applyFulfillmentStatus(OrderSearchCriteria criteria, OrderFulfillmentStatus status) {
        if (status.getOrderStatus() != null) {
            criteria.setOrderStatus(Collections.singletonList(status.getOrderStatus()));
        }
        criteria.setFulfillerStatuses(status.getFulfillerStatuses());
        criteria.setIncludeNullFulfillerStatus(status.getIncludeNullFulfillerStatus());
    }

    void printResult(OrderSearchResult result) {
        log.debug("Total count: {}", result.getTotalCount());
        for (Order order : result.getOrders()) {
            log.debug("Order: {}", order.getId());
            log.debug("======================");
            log.debug("  Order Type: {}", order.getOrderType().getName());
            log.debug("  Concept: {}", order.getConcept().getDisplayString());
            log.debug("  Date Activated: {}", order.getDateActivated());
            log.debug("  AutoExpire Date: {}", order.getAutoExpireDate());
            log.debug("  Date Stopped: {}", order.getDateStopped());
            log.debug("  Fulfiller Status: {}", order.getFulfillerStatus());
            log.debug("  ");
        }
    }
}
