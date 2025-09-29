package org.openmrs.module.pihapps.page.controller.admin;

import lombok.Data;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.context.AppContextModel;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.contextmodel.AppContextModelGenerator;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Administrative tool to facilitate authoring and testing app and extension expressions
 */
public class ExpressionTesterPageController {

    private static final String REQUIRED_PRIVILEGE = "App: coreapps.systemAdministration";

    public void controller(PageModel model,
                    @RequestParam(required = false, value = "patientRef") String patientRef,
                    @RequestParam(required = false, value = "visitRef") String visitRef,
                    @RequestParam(required = false, value = "dashboard") String dashboard,
                    @RequestParam(required = false, value = "extensionPoint") String extensionPoint,
                    @RequestParam(required = false, value = "extension") String extension,
                    @RequestParam(required = false, value = "expressionToTest") String expressionToTest,
                    @RequestParam(required = false, value = "app") AppDescriptor app,
                    @SpringBean("patientService") PatientService patientService,
                    @SpringBean("visitService") VisitService visitService,
                    @SpringBean("appContextModelGenerator") AppContextModelGenerator appContextModelGenerator,
                    @SpringBean("appFrameworkService") AppFrameworkService appFrameworkService,
                    UiSessionContext sessionContext) {

        Context.requirePrivilege(REQUIRED_PRIVILEGE);

        model.addAttribute("patientRef", patientRef);
        model.addAttribute("visitRef", visitRef);
        model.addAttribute("app", app == null ? "" : app.getId());
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("extensionPoint", extensionPoint);
        model.addAttribute("extension", extension);

        Patient patient = null;
        if (StringUtils.isNotEmpty(patientRef)) {
            patient = patientService.getPatientByUuid(patientRef);
            if (patient == null) {
                patient = patientService.getPatient(Integer.parseInt(patientRef));
            }
        }
        Visit visit = null;
        if (StringUtils.isNotEmpty(visitRef)) {
            visit = visitService.getVisitByUuid(visitRef);
            if (visit == null) {
                visit = visitService.getVisit(Integer.parseInt(visitRef));
            }
        }
        if (patient == null && visit != null) {
            patient = visit.getPatient();
        }

        AppContextModel contextModel;
        if (patient == null) {
            contextModel = sessionContext.generateAppContextModel();
        }
        else {
            contextModel = appContextModelGenerator.generateAppContextModel(sessionContext, patient, visit);
        }

        List<EvaluationResult> evaluationResults = new ArrayList<>();
        Set<String> extensionPoints = new TreeSet<>();
        for (Extension e : appFrameworkService.getAllEnabledExtensions()) {
            extensionPoints.add(e.getExtensionPointId());
            if (e.getId().equalsIgnoreCase(extension) && StringUtils.isBlank(expressionToTest)) {
                expressionToTest = e.getRequire();
            }
            if (e.getExtensionPointId().equalsIgnoreCase(extensionPoint)) {
                EvaluationResult result = new EvaluationResult();
                result.setExtension(e);
                try {
                    result.setPassed(appFrameworkService.checkRequireExpressionStrict(e, contextModel));
                }
                catch (Exception exception) {
                    result.setException(exception);
                }
                evaluationResults.add(result);
            }
        }
        evaluationResults.sort(Comparator.comparing(r -> r.getExtension().getId()));
        model.addAttribute("extensionPoints", extensionPoints);
        model.addAttribute("evaluationResults", evaluationResults);

        String requireExpressionContext = null;
        String requireExpressionContextException = null;
        try {
            requireExpressionContext = appFrameworkService.getRequireExpressionContext(contextModel);
        }
        catch (Exception e) {
            requireExpressionContextException = e.getMessage();
        }
        model.addAttribute("requireExpressionContext", requireExpressionContext);
        model.addAttribute("requireExpressionContextException", requireExpressionContextException);

        if (StringUtils.isNotBlank(expressionToTest)) {
            expressionToTest = StringEscapeUtils.unescapeHtml(expressionToTest);
        }

        model.addAttribute("expressionToTest", expressionToTest == null ? null : expressionToTest.trim());

        boolean expressionToTestResult = false;
        Exception expressionToTestException = null;
        try {
            Extension extensionToTest = new Extension();
            extensionToTest.setRequire(expressionToTest);
            expressionToTestResult = appFrameworkService.checkRequireExpressionStrict(extensionToTest, contextModel);
        }
        catch (Exception exception) {
            expressionToTestException = exception;
        }
        model.addAttribute("expressionToTestResult", expressionToTestResult);
        model.addAttribute("expressionToTestException", expressionToTestException);
    }

    @Data
    static class EvaluationResult {
        private Extension extension;
        private boolean passed;
        private Exception exception;
    }
}
