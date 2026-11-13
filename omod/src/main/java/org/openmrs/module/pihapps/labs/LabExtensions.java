package org.openmrs.module.pihapps.labs;

import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.service.AppFrameworkService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The extension points of the lab pages, and the extensions of one in the form the pages' scripts use
 */
public class LabExtensions {

    /**
     * Extensions whose extensionParams.concept is a result concept, shown as one row, and whose
     * extensionParams.jsFunction is given the result obs, with its members if it is a group, and returns, or resolves
     * to, the html or element to show as its result.  Used by the patient lab results page, and to show results being
     * validated
     */
    public static final String RESULT_RENDERERS = "org.openmrs.module.pihapps.patientLabResults.resultRenderers";

    /**
     * Extensions whose extensionParams.concept is a result concept, the ordered test or a member of the ordered panel,
     * and whose extensionParams.jsFunction creates the editor its result is entered with on the results forms.  See
     * labs/labResultFields.js
     */
    public static final String RESULT_EDITORS = "org.openmrs.module.pihapps.labResults.resultEditors";

    /**
     * @return the extensions of the extension point that the current user may use, each as its id, label, order and
     * extensionParams
     */
    public static List<Map<String, Object>> getExtensions(AppFrameworkService appFrameworkService, String extensionPoint) {
        List<Map<String, Object>> ret = new ArrayList<>();
        for (Extension ext : appFrameworkService.getExtensionsForCurrentUser(extensionPoint)) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", ext.getId());
            map.put("label", ext.getLabel());
            map.put("order", ext.getOrder());
            map.put("extensionParams", ext.getExtensionParams() != null ? ext.getExtensionParams() : new HashMap<>());
            ret.add(map);
        }
        return ret;
    }
}
