package org.openmrs.module.pihapps.orders;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PatientWithOrdersSearchResult {
    Long totalCount;
    List<PatientWithOrders> patients = new ArrayList<>();
}
