package org.openmrs.module.pihapps;

import org.junit.jupiter.api.Test;
import org.openmrs.Order;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
