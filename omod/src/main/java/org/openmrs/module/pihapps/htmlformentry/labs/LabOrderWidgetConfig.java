package org.openmrs.module.pihapps.htmlformentry.labs;

import lombok.Getter;
import org.openmrs.Concept;
import org.openmrs.module.htmlformentry.widget.OrderWidgetConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LabOrderWidgetConfig extends OrderWidgetConfig {

    public LabOrderWidgetConfig() {
        super();
        setAttributes(new LinkedHashMap<>());
    }

    @Getter
    private final Map<Concept, List<Concept>> orderablesByCategory = new LinkedHashMap<>();

    @Getter
    private final Map<Concept, List<Concept>> orderReasonsByOrderable = new HashMap<>();

}
