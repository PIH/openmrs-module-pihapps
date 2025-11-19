package org.openmrs.module.pihapps;

import org.junit.jupiter.api.Test;
import org.openmrs.Order;
import org.openmrs.module.pihapps.labs.LabOrderSearchCriteria;
import org.openmrs.module.pihapps.labs.LabOrderSearchResult;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class PihAppsServiceTest extends BaseModuleContextSensitiveTest {

    private static final Logger log = LoggerFactory.getLogger(PihAppsServiceTest.class);

    @Autowired
    @Qualifier("pihappsService")
    private PihAppsService pihAppsService;

    @Test
    public void shouldGetPagedLabOrders() {
        LabOrderSearchCriteria searchCriteria = new LabOrderSearchCriteria();
        LabOrderSearchResult result = pihAppsService.getLabOrders(searchCriteria);
        assertThat(result.getTotalCount(), equalTo(3L));
        assertThat(result.getOrders().size(), equalTo(3));
        printResult(result);
        searchCriteria.setStartIndex(0);
        searchCriteria.setLimit(2);
        result = pihAppsService.getLabOrders(searchCriteria);
        assertThat(result.getTotalCount(), equalTo(3L));
        assertThat(result.getOrders().size(), equalTo(2));
        searchCriteria.setStartIndex(2);
        result = pihAppsService.getLabOrders(searchCriteria);
        assertThat(result.getTotalCount(), equalTo(3L));
        assertThat(result.getOrders().size(), equalTo(1));
    }

    void printResult(LabOrderSearchResult result) {
        log.info("Total count: {}", result.getTotalCount());
        for (Order order : result.getOrders()) {
            log.info("Order: {}", order.getId());
            log.info("======================");
            log.info("  Order Type: {}", order.getOrderType().getName());
            log.info("  Concept: {}", order.getConcept().getDisplayString());
            log.info("  Date Activated: {}", order.getDateActivated());
            log.info("  AutoExpire Date: {}", order.getAutoExpireDate());
            log.info("  Date Stopped: {}", order.getDateStopped());
            log.info("  Fulfiller Status: {}", order.getFulfillerStatus());
            log.info("  ");
        }
    }
}
