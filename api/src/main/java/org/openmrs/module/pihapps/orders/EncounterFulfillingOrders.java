package org.openmrs.module.pihapps.orders;

import lombok.Data;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;

import java.util.List;

@Data
public class EncounterFulfillingOrders {
    Encounter encounter; // This is the encounter that fulfills the given orders
    List<Order> orders; // These are the orders that this encounter fulfills, typically associated with a different encounter
    List<EditorResult> editorResults; // Results from resultEditors extensions, each replacing the order's existing result of its concept

    /**
     * A result of an order from a resultEditors extension: a single obs or an obs group, of the given concept.  When
     * saved, it replaces the order's existing non-voided obs of the concept in the encounter: they are
     * voided, with their members, and obs, if not null, is added with its members, linked to the order.
     */
    @Data
    public static class EditorResult {
        Order order;
        Concept concept;
        Obs obs;
    }
}
