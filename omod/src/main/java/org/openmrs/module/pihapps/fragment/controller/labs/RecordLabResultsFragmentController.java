package org.openmrs.module.pihapps.fragment.controller.labs;

import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.pihapps.labs.LabExtensions;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentModel;

/**
 * The single order results form
 */
public class RecordLabResultsFragmentController {

    public void controller(FragmentModel model, @SpringBean AppFrameworkService appFrameworkService) {
        model.addAttribute("resultEditorExtensions", LabExtensions.getExtensions(appFrameworkService, LabExtensions.RESULT_EDITORS));
    }
}
