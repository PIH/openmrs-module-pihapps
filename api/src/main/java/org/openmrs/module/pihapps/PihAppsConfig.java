package org.openmrs.module.pihapps;

import lombok.Getter;
import org.openmrs.PatientIdentifierType;
import org.openmrs.module.emrapi.EmrApiProperties;
import org.openmrs.module.pihapps.labs.LabOrderConfig;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PihAppsConfig {

    @Getter
    private final LabOrderConfig labOrderConfig;

    @Getter
    private final EmrApiProperties emrApiProperties;

    @Autowired
    public PihAppsConfig(LabOrderConfig labOrderConfig, EmrApiProperties emrApiProperties) {
        this.labOrderConfig = labOrderConfig;
        this.emrApiProperties = emrApiProperties;
    }

    public String getDashboardUrl() {
        String defaultUrl = "/coreapps/clinicianfacing/patient.page?patientId={{patientId}}";
        return ConfigUtil.getProperty("coreapps.dashboardUrl", defaultUrl);
    }

    public PatientIdentifierType getPrimaryIdentifierType() {
        return emrApiProperties.getPrimaryIdentifierType();
    }
}
