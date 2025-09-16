package org.openmrs.module.pihapps;

import lombok.Getter;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.api.ConceptNameType;
import org.openmrs.api.context.Context;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PihAppsConfig {

    @Getter
    private final LabOrderConfig labOrderConfig;

    @Autowired
    public PihAppsConfig(LabOrderConfig labOrderConfig) {
        this.labOrderConfig = labOrderConfig;
    }

    public String getDashboardUrl() {
        String defaultUrl = "/coreapps/clinicianfacing/patient.page?patientId={{patientId}}";
        return ConfigUtil.getProperty("coreapps.dashboardUrl", defaultUrl);
    }
}
