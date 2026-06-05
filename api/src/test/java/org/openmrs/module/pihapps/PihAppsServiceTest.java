package org.openmrs.module.pihapps;

import org.junit.jupiter.api.Test;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
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
