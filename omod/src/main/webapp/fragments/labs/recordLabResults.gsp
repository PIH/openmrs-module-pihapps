<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")

    ui.includeJavascript("uicommons", "datetimepicker/bootstrap-datetimepicker.min.js")
    if (org.openmrs.api.context.Context.getLocale().getLanguage() != "en") {
        ui.includeJavascript("uicommons", "datetimepicker/locales/bootstrap-datetimepicker.${ org.openmrs.api.context.Context.getLocale() }.js")
    }
    ui.includeCss("uicommons", "datetimepicker.css")

    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeJavascript("pihapps", "formHelper.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "labs/labResultFields.js")
    ui.includeCss("pihapps", "labs/labs.css")

    config.require("id")
    def id = config.id
%>

<script type="text/javascript">
    function initializeLabResultsForm(formConfig) {

        const order = formConfig.order;
        const pihAppsConfig = formConfig.pihAppsConfig;
        const onSuccessFunction = formConfig.onSuccessFunction;
        const onCancelFunction = formConfig.onCancelFunction;

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);
        parentElement.find(".errors-section").html("");

        const messages = {
            estimated: '${ ui.encodeJavaScript(ui.message("pihapps.estimated")) }',
            valueMustBeInteger: '${ ui.encodeJavaScript(ui.message("pihapps.valueCannotBeDecimal")) }',
            abnormalValue: '${ ui.encodeJavaScript(ui.message("pihapps.abnormalValue")) }',
            criticalValue: '${ ui.encodeJavaScript(ui.message("pihapps.criticalValue")) }',
            minimumAllowedValue: '${ ui.encodeJavaScript(ui.message("pihapps.minimumAllowedValue")) }',
            maximumAllowedValue: '${ ui.encodeJavaScript(ui.message("pihapps.maximumAllowedValue")) }',
            resultDateCannotBeFuture: '${ ui.encodeJavaScript(ui.message("pihapps.resultDateCannotBeFuture")) }',
            resultDateCannotBeBeforeSpecimenDate: '${ ui.encodeJavaScript(ui.message("pihapps.resultDateCannotBeBeforeSpecimenDate")) }',
            resultsMustHaveAssociatedSpecimenEncounter: '${ ui.encodeJavaScript(ui.message("pihapps.resultsMustHaveAssociatedSpecimenEncounter")) }',
            noResultsEntered: '${ ui.encodeJavaScript(ui.message("pihapps.noResultsEntered")) }',
            errorsWithOneOrMoreFields: '${ ui.encodeJavaScript(ui.message("pihapps.errorsWithOneOrMoreFields")) }',
            reasonRequired: '${ ui.encodeJavaScript(ui.message("pihapps.reasonRequired")) }',
            addComment: '${ ui.encodeJavaScript(ui.message("pihapps.addComment")) }',
            removeComment: '${ ui.encodeJavaScript(ui.message("pihapps.removeComment")) }',
            removeResult: '${ ui.encodeJavaScript(ui.message("pihapps.removeResult")) }',
            addAnotherResult: '${ ui.encodeJavaScript(ui.message("pihapps.addAnotherResult")) }',
            submitForValidation: '${ ui.encodeJavaScript(ui.message("pihapps.submitForValidation")) }',
            unableToSave: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.unableToSave")) }',
            unableToLoadResultEntry: '${ ui.encodeJavaScript(ui.message("pihapps.unableToLoadResultEntry")) }',
        };

        // Results recorded as obs groups that an implementation's own editor enters (see labResultFields.js)
        const resultEditors = PihAppsLabResultFields.getResultEditors(${ ui.toJson(resultEditorExtensions) });
        const editors = [];

        if (!order || !order.fulfillerEncounter) {
            console.warn("recordLabResults requires an order that has a non-null fulfillerEncounter")
            return;
        }

        const resetForm = () => {
            parentElement.find(".errors-section").html("");
            parentElement.find(".order-and-specimen-section").hide();
            parentElement.find(".result-entry-section").hide();
            parentElement.find(".loading-section").show();
        }

        const formLoaded = () => {
            parentElement.find(".loading-section").hide();
            parentElement.find(".order-and-specimen-section").show();
            parentElement.find(".result-entry-section").show();
        }

        resetForm();

        const locale = window.sessionContext?.locale ?? 'en';
        moment.locale(locale);
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);

        const obsRep = "uuid,concept:(uuid,datatype:(name)),value,valueCoded:(uuid,display),valueDatetime,valueText,valueNumeric,comment,formNamespaceAndPath,order:(uuid)"
        const specimenEncounterRep = "uuid,encounterDatetime,encounterType:(uuid),location:(uuid,display),encounterProviders:(provider:(uuid,display),encounterRole:(uuid,display)),obs:(" + obsRep + ",groupMembers:(" + obsRep + ",groupMembers:(" + obsRep + ")))";
        jq.get(openmrsContextPath + "/ws/rest/v1/encounter/" + order.fulfillerEncounter.uuid + "?v=custom:(" + specimenEncounterRep + ")", function (fulfillerEncounter) {

            const formHelper = new FormHelper({
                jq: jq,
                moment: moment,
                locale: locale,
                dateFormat: pihAppsConfig.dateFormat,
                dateTimeFormat: pihAppsConfig.dateTimeFormat,
                formName: "pihapps^labResultForm",
                encounter: fulfillerEncounter,
                patientUuid: order.patient.uuid
            });

            const labIdQuestion = pihAppsConfig.labOrderConfig.labIdentifierConcept;
            const estimatedCollectionDateQuestion = pihAppsConfig.labOrderConfig.estimatedCollectionDateQuestion;
            const estimatedCollectionDateAnswer = pihAppsConfig.labOrderConfig.estimatedCollectionDateAnswer;
            const receivedDateQuestion = pihAppsConfig.labOrderConfig.specimenReceivedDateQuestion;
            const testLocationQuestion = pihAppsConfig.labOrderConfig.testLocationQuestion;
            const resultDateQuestion = pihAppsConfig.labOrderConfig.resultsDateQuestion;
            const fulfillerStatusOptions = pihAppsConfig.labOrderConfig.fulfillerStatusOptions;

            // Populate top sections containing specimen and order details

            const orderAndSpecimenSection = parentElement.find(".order-and-specimen-section");
            const encounterRole = pihAppsConfig.labOrderConfig.specimenCollectionEncounterRole?.uuid;
            if (!encounterRole) {
                parentElement.find(".specimen-provider-section").hide();
            }

            orderAndSpecimenSection.find(".orderable").html(order.concept.displayStringForLab);
            orderAndSpecimenSection.find(".order-date").html(dateUtils.formatDateWithTimeIfPresent(order.dateActivated));
            orderAndSpecimenSection.find(".test-ordered-by").html(order.orderer?.display);
            orderAndSpecimenSection.find(".order-number").html(order.orderNumber);
            orderAndSpecimenSection.find(".order-location").html(order.encounter.location?.display);

            const labId = formHelper.getInitialObsValue(labIdQuestion.uuid)?.valueText;
            const estimated = formHelper.getInitialObsValue(estimatedCollectionDateQuestion.uuid)?.valueCoded?.uuid === estimatedCollectionDateAnswer.uuid;
            const receivedDate = formHelper.getInitialObsValue(receivedDateQuestion.uuid)?.valueDatetime;
            const testLocation = formHelper.getInitialObsValue(testLocationQuestion.uuid)?.valueCoded?.display;
            const providers = fulfillerEncounter.encounterProviders?.map((p) => p.display).join(", ");
            const estimatedText = estimated ? '<span class="estimated-text">(' + messages.estimated + ")</span>" : "";

            orderAndSpecimenSection.find(".lab-id").html(labId);
            orderAndSpecimenSection.find(".specimen-collection-date").html(dateUtils.formatDateWithTimeIfPresent(fulfillerEncounter.encounterDatetime) + estimatedText);
            orderAndSpecimenSection.find(".specimen-collection-location").html(fulfillerEncounter.location?.display);
            orderAndSpecimenSection.find(".specimen-received-date").html(dateUtils.formatDateWithTimeIfPresent(receivedDate));
            orderAndSpecimenSection.find(".lab-test-location").html(testLocation);
            if (providers) {
                orderAndSpecimenSection.find(".specimen-collected-by").html(providers);
            }
            else {
                orderAndSpecimenSection.find(".specimen-collected-by").parent().hide();
            }

            // Remove any previously populated widgets/fields
            const resultsEntrySection = parentElement.find(".result-entry-section");
            resultsEntrySection.find(".result-field").empty();
            resultsEntrySection.find(".result-row").remove();

            // Add fulfiller status widget
            // When results must be validated, completing them submits them for validation: the order stays
            // in progress, awaiting validation, until a validator completes it
            // For results awaiting validation, "In progress" is not offered: saving it would look the same to the
            // server as re-saving the specimen, so the order would stay awaiting validation
            // For a completed order, saving amends its results, so it is not offered as a submission either
            const requireResultValidation = pihAppsConfig.labOrderConfig.requireResultValidation;
            const validationEventType = order.fulfillerStatus === 'IN_PROGRESS' ? order.labResultValidation?.eventType : null;
            const awaitingValidation = requireResultValidation && validationEventType === 'RESULTS_SUBMITTED';
            const returnedForCorrection = requireResultValidation && validationEventType === 'RESULTS_RETURNED';
            const completed = requireResultValidation && order.fulfillerStatus === 'COMPLETED';
            const fulfillerStatuses = ((awaitingValidation || completed) ? ["COMPLETED", "EXCEPTION"] : ["IN_PROGRESS", "COMPLETED", "EXCEPTION"])
                .map(s => { return fulfillerStatusOptions.find(o => o.status === s) })
                .map(o => { return { value: o.status, display: (requireResultValidation && !completed && o.status === 'COMPLETED') ? messages.submitForValidation : o.display }})
            ;
            const fulfillerStatusWidget = formHelper.createSelectWidget({
                id: id + "-fulfiller-status",
                options: fulfillerStatuses,
                // Results awaiting validation or returned for correction open as submitted, with their results shown
                initialValue: (awaitingValidation || returnedForCorrection) ? 'COMPLETED' : order.fulfillerStatus,
                includeEmptyOption: false,
            });
            resultsEntrySection.find(".fulfiller-status").append(fulfillerStatusWidget);
            formHelper.displaySelectWidgetAsButtons(fulfillerStatusWidget, {
                buttonClass: "btn-outline-secondary"
            });

            const returnedSection = resultsEntrySection.find(".returned-for-correction-section");
            returnedSection.find(".returned-for-correction-comment").text(returnedForCorrection ? (order.labResultValidation.comment || "") : "");
            returnedSection.toggle(returnedForCorrection);

            // Changing the status only shows its section: what the other sections hold is cleared when saving, so that
            // choosing another status and back keeps what was entered
            fulfillerStatusWidget.on("change", () => {
                const fulfillerStatus = fulfillerStatusWidget.val();
                parentElement.find(".status-section").hide();
                parentElement.find(".status-section-" + fulfillerStatus).show();
            }).change();

            // Add reason not performed widget
            const reasonQuestion = pihAppsConfig.labOrderConfig.reasonTestNotPerformedQuestion;
            const reasonPicker = formHelper.createObsWidget(reasonQuestion, {
                id: id + "reason-not-performed",
                name: "reason-not-performed",
                orderUuid: order.uuid
            });
            parentElement.find(".result-field.reason-not-performed").empty().append(reasonPicker);

            // Add result date widget
            const resultDateWidget = formHelper.createObsWidget(resultDateQuestion, {
                id:  id + "-result-date",
                name: "result-date",
                orderUuid: order.uuid
            });
            resultsEntrySection.find(".result-date").append(resultDateWidget);

            // Add result widgets
            const resultSection = resultsEntrySection.find(".result-fields");
            jq.get(openmrsContextPath + "/ws/rest/v1/concept/" + order.concept.uuid + "?v=custom:(" + PihAppsLabResultFields.orderableRep + ")", function (orderable) {
                PihAppsLabResultFields.render({
                    jq: jq,
                    formHelper: formHelper,
                    order: order,
                    orderable: orderable,
                    container: resultSection,
                    idPrefix: id,
                    collectResultComments: pihAppsConfig.labOrderConfig.collectResultComments,
                    messages: messages,
                    resultEditors: resultEditors,
                    editors: editors
                });

                const cancelButton = parentElement.find(".action-button.cancel");
                cancelButton.off("click");
                cancelButton.on("click", (event) => {
                    event.preventDefault();
                    resetForm();
                    if (onCancelFunction) {
                        onCancelFunction();
                    }
                });

                const saveButton = parentElement.find(".action-button.confirm");
                saveButton.off("click");
                saveButton.on("click", (event) => {

                    event.preventDefault();
                    parentElement.find(".action-button").attr("disabled", "disabled");
                    parentElement.find(".errors-section").html("");

                    const fulfillerStatusToSubmit = jq("#" + id + "-fulfiller-status").val();
                    // What the sections of the other statuses hold is not saved: results unless completed (the editors'
                    // as the fields'), the reason unless not performed
                    const otherSections = parentElement.find(".status-section").not(".status-section-" + fulfillerStatusToSubmit);
                    otherSections.find(":checkbox, :radio").prop("checked", false);
                    otherSections.find(":input").not(":checkbox, :radio, button").val("");
                    if (fulfillerStatusToSubmit !== 'COMPLETED') {
                        editors.forEach(editor => editor.clear());
                    }
                    const encounterToSubmit = formHelper.constructEncounterPayload();
                    const ordersToSubmit = [{ uuid: order.uuid, fulfillerStatus: fulfillerStatusToSubmit }];

                    // Validate
                    const errors = [];
                    const currentDate = moment();

                    const resultDateStr = encounterToSubmit.obs.find(o => o.concept === resultDateQuestion.uuid)?.valueDatetime;
                    if (resultDateStr) {
                        const resultDate = moment(resultDateStr);
                        if (resultDate.isAfter(currentDate)) {
                            errors.push(messages.resultDateCannotBeFuture);
                        }
                        // Result date is currently set as a Date obs, not a Datetime, so just validate against the date portion
                        // Compare literal calendar dates (not via moment) to avoid browser-vs-server timezone issues
                        const resultDateOnly = resultDateStr.substring(0, 10);
                        const encounterDateOnly = encounterToSubmit.encounterDatetime.substring(0, 10);
                        if (encounterDateOnly > resultDateOnly) {
                            errors.push(messages.resultDateCannotBeBeforeSpecimenDate);
                        }
                    }

                    // If not performed, then reason is required
                    if (fulfillerStatusToSubmit === "EXCEPTION") {
                        const reasonObs = encounterToSubmit.obs.find(o => o.concept === reasonQuestion.uuid);
                        if (!reasonObs || !reasonObs.valueCoded) {
                            errors.push(messages.reasonRequired);
                        }
                    }

                    const fieldErrors = parentElement.find(".field-error.error-value").get().map(element => jq(element).html().trim()).filter(Boolean);
                    if (fieldErrors.length > 0) {
                        errors.push(messages.errorsWithOneOrMoreFields);
                    }

                    if (errors && errors.length > 0) {
                        errors.forEach(e => {
                            parentElement.find(".errors-section").append(jq("<div>").html(e));
                        });
                        parentElement.find(".action-button").removeAttr("disabled");
                        return;
                    }

                    const showSaveError = (xhr) => {
                        parentElement.find(".action-button").removeAttr("disabled");
                        const error = xhr?.responseJSON?.error ?? xhr?.responseJSON;
                        const message = error?.translatedMessage ?? error?.message ?? (typeof error === "string" ? error : null) ?? messages.unableToSave;
                        parentElement.find(".errors-section").text(message);
                    };

                    // The results entered with obs group editors are saved with the others, in one request
                    const payload = {
                        encounter: encounterToSubmit,
                        orders: ordersToSubmit,
                        editorResults: PihAppsLabResultFields.getEditorResults(editors)
                    };
                    jq.ajax({
                        url: openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders/" + encounterToSubmit.uuid,
                        type: "POST",
                        contentType: "application/json; charset=utf-8",
                        data: JSON.stringify(payload),
                        dataType: "json",
                        success: () => {
                            parentElement.find(".action-button").removeAttr("disabled");
                            resetForm();
                            onSuccessFunction();
                        },
                        error: showSaveError
                    });
                });

                formLoaded();
            });
        });
    }
</script>

<style>
    fieldset {
        margin-top: 10px;
    }
    legend {
        font-weight: bold; font-size: 1.1em;
    }
    .estimated-text {
        font-size: smaller;
        padding-left: 10px;
    }
    .result-field-labels {
       text-decoration: underline;
        padding-bottom: 10px;
    }
    .status-row {
        padding-bottom: 3px;
    }
    .result-row {
        padding-bottom: 3px;
    }
    .result-numeric-input {
        display: inline-block;
        min-width: unset;
        width: 125px;
    }
    .result-units {
        display: inline-block;
        width: 90px;
        padding-left: 10px;
    }
    /* The field, units and comment link on one line; an invalid value's message and the comment below them */
    .result-entry-section .obs-widget {
        display: inline-flex;
        flex-wrap: wrap;
        align-items: center;
    }
    .result-entry-section .obs-widget .comment-section {
        flex-basis: 100%;
        order: 2;
    }
</style>

<div id="${id}">
    <div class="loading-section"><i class="icon-spinner icon-spin icon-4x"></i></div>
    <div class="errors-section"></div>
    <div class="row order-and-specimen-section">
        <div class="col">
            <fieldset class="specimen-details-section">
                <legend>${ ui.message("pihapps.specimenCollectionDetails") }</legend>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.labId") }</span>
                    <span class="col lab-id"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.specimenCollectionDate") }</span>
                    <span class="col specimen-collection-date"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.specimenCollectedBy") }</span>
                    <span class="col specimen-collected-by"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.specimenCollectionLocation") }</span>
                    <span class="col specimen-collection-location"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.specimenReceivedDate") }</span>
                    <span class="col specimen-received-date"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.labTestLocation") }</span>
                    <span class="col lab-test-location"></span>
                </div>
            </fieldset>
        </div>
        <div class="col">
            <fieldset class="order-details-section">
                <legend>${ ui.message("pihapps.orderDetails") }</legend>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.labTest") }</span>
                    <span class="col orderable"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.orderDate") }</span>
                    <span class="col order-date"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.orderLocation") }</span>
                    <span class="col order-location"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.testOrderedBy") }</span>
                    <span class="col test-ordered-by"></span>
                </div>
                <div class="order-detail-component row align-items-start">
                    <span class="col">${ ui.message("pihapps.orderNumber") }</span>
                    <span class="col order-number"></span>
                </div>
            </fieldset>
        </div>
    </div>
    <div class="result-entry-section">
        <div class="returned-for-correction-section alert alert-warning" style="display: none;">
            <strong>${ ui.message("pihapps.orderFulfillmentEventType.RESULTS_RETURNED") }:</strong>
            <span class="returned-for-correction-comment"></span>
        </div>
        <div class="result-status-section">
            <fieldset>
                <legend>${ ui.message("pihapps.status") }</legend>
                <div class="row status-row align-items-start">
                    <span class="col-3 result-field-label">${ ui.message("pihapps.status") }</span>
                    <span class="col-auto result-field fulfiller-status"></span>
                </div>
                <div class="row status-row align-items-start status-section status-section-EXCEPTION">
                    <span class="col-3 result-field-label">${ ui.message("pihapps.reason") }</span>
                    <span class="col-auto result-field reason-not-performed"></span>
                </div>
                <div class="row status-row align-items-start status-section status-section-COMPLETED">
                    <span class="col-3 result-field-label">${ ui.message("pihapps.resultDate") }</span>
                    <span class="col-auto result-field result-date"></span>
                </div>
            </fieldset>
        </div>
        <div class="result-section status-section status-section-COMPLETED">
            <fieldset>
                <legend>${ ui.message("pihapps.results") }</legend>
                <div class="result-fields">
                    <div class="row align-items-start result-field-labels">
                        <span class="col-3 result-field-label">${ ui.message("pihapps.labTest") }</span>
                        <span class="col-6 result-field-label">${ ui.message("pihapps.result") }</span>
                        <span class="col-3 result-field-label">${ ui.message("pihapps.normalRange") }</span>
                    </div>
                </div>
            </fieldset>
        </div>
        <br><br>
        <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
        <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
    </div>
</div>