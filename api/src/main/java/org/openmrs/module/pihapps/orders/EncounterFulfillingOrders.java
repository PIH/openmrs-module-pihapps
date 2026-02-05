package org.openmrs.module.pihapps.orders;

import lombok.Data;
import org.openmrs.Encounter;
import org.openmrs.Order;

import java.util.List;

@Data
public class EncounterFulfillingOrders {
    Encounter encounter; // This is the encounter that fulfills the given orders
    List<Order> orders; // These are the orders that this encounter fulfills, typically associated with a different encounter
}
