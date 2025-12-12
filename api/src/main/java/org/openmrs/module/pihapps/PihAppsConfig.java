package org.openmrs.module.pihapps;

import lombok.Getter;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.EmrApiProperties;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
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

    public String getDateFormat() {
        return ConfigUtil.getProperty("uiframework.formatter.JSdateFormat", "DD-MMM-YYYY");
    }

    public String getDateTimeFormat() {
        return ConfigUtil.getProperty("uiframework.formatter.JSdateAndTimeFormat", "DD-MMM-YYYY HH:mm");
    }

    public PatientIdentifierType getPrimaryIdentifierType() {
        return emrApiProperties.getPrimaryIdentifierType();
    }

    public String getLocale() {
        return Context.getLocale().toString();
    }

    public String getLanguage() {
        return Context.getLocale().getLanguage();
    }
}
