package org.openmrs.module.pihapps.orders;

import lombok.Data;
import org.openmrs.Concept;

import java.util.ArrayList;
import java.util.List;

@Data
public class LabTestCategory {
    private Concept category;
    private List<Concept> labTests = new ArrayList<>();
}
