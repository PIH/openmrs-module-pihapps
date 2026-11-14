package org.openmrs.module.pihapps.page.controller.labs;

import org.openmrs.module.pihapps.labs.LabExtensions;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Enters or validates the lab results of a patient by Lab ID: the collected tests of one Lab ID that are not completed
 * (in validate mode, those awaiting validation), with a section for each lab test category.  When the patient has
 * several such Lab IDs, the page offers a choice between them; labId selects one.  The single order results form on
 * the lab order list (fragment labs/recordLabResults) enters or edits the results of one order, including completed
 * ones.
 */
public class LabResultsByLabIdPageController {

    public static final String MODE_ENTER = "enter";
    public static final String MODE_VALIDATE = "validate";

    public void get(PageModel model,
                    @RequestParam("patient") Patient patient,
                    @RequestParam(value = "mode", required = false) String mode,
                    @RequestParam(value = "labId", required = false) String labId,
                    @SpringBean AppFrameworkService appFrameworkService) {
        boolean validate = MODE_VALIDATE.equals(mode);
        boolean authorized = validate ? Context.hasPrivilege(LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS)
                : Context.hasPrivilege(PrivilegeConstants.ADD_ENCOUNTERS);
        model.addAttribute("authorized", authorized);
        model.addAttribute("patient", patient);
        model.addAttribute("mode", validate ? MODE_VALIDATE : MODE_ENTER);
        model.addAttribute("labId", labId);
        model.addAttribute("resultEditorExtensions", LabExtensions.getExtensions(appFrameworkService, LabExtensions.RESULT_EDITORS));
        model.addAttribute("resultRendererExtensions", LabExtensions.getExtensions(appFrameworkService, LabExtensions.RESULT_RENDERERS));
    }
}
