package org.openmrs.module.pihapps.labs;

import lombok.Data;
import org.openmrs.Order;

import java.util.List;

@Data
public class LabOrderSearchResult {
    LabOrderSearchCriteria criteria;
    Long totalCount;
    List<Order> orders;
}
