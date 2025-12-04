package org.openmrs.module.pihapps.orders;

import lombok.Data;
import org.openmrs.Order;

import java.util.List;

@Data
public class OrderSearchResult {
    OrderSearchCriteria criteria;
    Long totalCount;
    List<Order> orders;
}
