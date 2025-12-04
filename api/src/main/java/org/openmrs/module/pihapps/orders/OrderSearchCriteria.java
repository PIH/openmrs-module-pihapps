package org.openmrs.module.pihapps.orders;

import lombok.Data;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.module.pihapps.SortCriteria;

import java.util.Date;
import java.util.List;

@Data
public class OrderSearchCriteria {
    private Patient patient;
    private List<OrderType> orderTypes;
    private Concept concept;
    private String accessionNumber;
    private Date activatedOnOrBefore;
    private Date activatedOnOrAfter;
    private List<OrderStatus> orderStatus;
    private List<Order.FulfillerStatus> fulfillerStatuses;
    private Boolean includeNullFulfillerStatus;
    private List<SortCriteria> sortCriteria;
    private Integer startIndex;
    private Integer limit;
}
