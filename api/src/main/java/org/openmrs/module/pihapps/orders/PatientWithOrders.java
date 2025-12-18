package org.openmrs.module.pihapps.orders;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.openmrs.Order;
import org.openmrs.Patient;

import java.util.List;

@Data
@AllArgsConstructor
public class PatientWithOrders {
    Patient patient;
    List<Order> orders;
}
