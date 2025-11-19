package org.openmrs.module.pihapps.labs;

import lombok.Data;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.module.pihapps.SortCriteria;

import java.util.Date;
import java.util.List;

@Data
public class LabOrderSearchCriteria {
    private Patient patient;
    private Concept labTest;
    private String accessionNumber;
    private Date activatedOnOrBefore;
    private Date activatedOnOrAfter;
    private List<OrderStatus> orderStatus;
    private List<Order.FulfillerStatus> fulfillerStatuses;
    private List<SortCriteria> sortCriteria;
    private Integer startIndex;
    private Integer limit;
}
