package org.openmrs.module.pihapps;

import lombok.Getter;
import org.openmrs.module.appframework.context.AppContextModel;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.feature.FeatureToggleProperties;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ExtensionConfig {

    @Getter
    private final AppFrameworkService appFrameworkService;

    @Getter
    private final FeatureToggleProperties featureToggleProperties;

    @Autowired
    public ExtensionConfig(AppFrameworkService appFrameworkService, FeatureToggleProperties featureToggleProperties) {
        this.appFrameworkService = appFrameworkService;
        this.featureToggleProperties = featureToggleProperties;
    }

    /**
     * @return the extensions for the current user/appContextModel for the given extension point that are not toggled off
     */
    public List<Extension> getExtensions(String extensionPoint, AppContextModel appContextModel) {
        List<Extension> extensions = appFrameworkService.getExtensionsForCurrentUser(extensionPoint, appContextModel);
        for (int i = 0; i < extensions.size(); i++) {
            Extension extension = extensions.get(i);
            if (extension.getExtensionParams() != null) {
                String featureToggle = (String) extension.getExtensionParams().get("featureToggle");
                if (featureToggle != null && !featureToggleProperties.isFeatureEnabled(featureToggle)) {
                    extensions.remove(i);
                    i--;
                }
            }
        }
        Collections.sort(extensions);
        return extensions;
    }
}
