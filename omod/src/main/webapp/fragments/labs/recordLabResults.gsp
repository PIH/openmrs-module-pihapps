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
    ui.includeCss("pihapps", "labs/labs.css")

    config.require("id")
    def id = config.id
%>

<script type="text/javascript">
    function initializeLabResultsForm(formConfig) {

        const order = formConfig.order;
        const pihAppsConfig = formConfig.pihAppsConfig;
        const onSuccessFunction = formConfig.onSuccessFunction;

        const messages = {
            estimated: '${ ui.message("pihapps.estimated") }',
            valueMustBeInteger: '${ ui.message("pihapps.valueCannotBeDecimal") }',
            abnormalValue: '${ ui.message("pihapps.abnormalValue") }',
            criticalValue: '${ ui.message("pihapps.criticalValue") }',
            minimumAllowedValue: '${ ui.message("pihapps.minimumAllowedValue") }',
            maximumAllowedValue: '${ ui.message("pihapps.maximumAllowedValue") }',
            resultDateCannotBeFuture: '${ ui.message("pihapps.resultDateCannotBeFuture") }',
            resultDateCannotBeBeforeSpecimenDate: '${ ui.message("pihapps.resultDateCannotBeBeforeSpecimenDate") }',
            resultsMustHaveAssociatedSpecimenEncounter: '${ ui.message("pihapps.resultsMustHaveAssociatedSpecimenEncounter") }',
            noResultsEntered: '${ ui.message("pihapps.noResultsEntered") }',
            errorsWithOneOrMoreFields: '${ ui.message("pihapps.errorsWithOneOrMoreFields") }',
        };

        if (!order || !order.fulfillerEncounter) {
            console.warn("recordLabResults requires an order that has a non-null fulfillerEncounter")
            return;
        }

        const locale = window.sessionContext?.locale ?? 'en';
        moment.locale(locale);
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);
        const currentDatetime = dateUtils.roundDownToNearestMinuteInterval(new Date(), 5);

        const obsRep = "uuid,concept:(uuid,datatype:(name)),value,valueCoded:(uuid,display),valueDatetime,valueText,valueNumeric,comment,formNamespaceAndPath"
        const specimenEncounterRep = "uuid,encounterDatetime,encounterType:(uuid),location:(uuid,display),encounterProviders:(provider:(uuid,display),encounterRole:(uuid,display)),obs:(" + obsRep + ",groupMembers:(" + obsRep + "))";
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

            const id = "${id}";
            const selectorPrefix = "#" + id;
            const parentElement = jq(selectorPrefix);
            parentElement.find(".errors-section").html("");

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
            const fulfillerStatusWidget = formHelper.createSelectWidget({
                id: id + "-fulfiller-status",
                options: fulfillerStatusOptions.filter(o => ["IN_PROGRESS", "COMPLETED", "EXCEPTION"].includes(o.status)).map(o => { return { value: o.status, display: o.display } }),
                initialValue: order.fulfillerStatus,
                includeEmptyOption: false
            });
            resultsEntrySection.find(".fulfiller-status").append(fulfillerStatusWidget);
            fulfillerStatusWidget.on("change", () => {
                const fulfillerStatus = fulfillerStatusWidget.val();
                parentElement.find(".status-section").hide().find(":input").val("");
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

            // Returns a validation result for a numeric field - with result type of [error, critical, abnormal], and message
            const validateNumericResult = function(messages, concept, refRange, value) {
                if (value && refRange) {
                    const numericValue = +value;
                    if (!concept.allowDecimal && !Number.isInteger(numericValue)) {
                        return {type: "error", message: messages.valueMustBeInteger};
                    }
                    if (refRange.lowAbsolute && value < refRange.lowAbsolute) {
                        return {type: "error", message: messages.minimumAllowedValue + ": " + refRange.lowAbsolute};
                    }
                    if (refRange.hiAbsolute && value > refRange.hiAbsolute) {
                        return {type: "error", message: messages.maximumAllowedValue + ": " + refRange.hiAbsolute};
                    }
                    if ((refRange.lowCritical && value < refRange.lowCritical) || (refRange.hiCritical && value > refRange.hiCritical)) {
                        return {type: "critical", message: messages.criticalValue};
                    }
                    if ((refRange.lowNormal && value < refRange.lowNormal) || (refRange.hiNormal && value > refRange.hiNormal)) {
                        return {type: "abnormal", message: messages.abnormalValue};
                    }
                }
                return null;
            }

            // Add result widgets
            const resultSection = resultsEntrySection.find(".result-fields");
            const baseConceptRep = "uuid,display,displayStringForLab,datatype:(uuid,name),allowDecimal,units";
            const baseConceptRepWithAnswers = baseConceptRep + ",answers:(" + baseConceptRep + ")";
            const testRep = baseConceptRepWithAnswers + ",setMembers:(" + baseConceptRepWithAnswers + ")";
            jq.get(openmrsContextPath + "/ws/rest/v1/concept/" + order.concept.uuid + "?v=custom:(" + testRep + ")", function (orderable) {
                const isPanel = orderable.setMembers.length > 0;
                const tests = isPanel ? orderable.setMembers : [orderable];
                tests.forEach((concept) => {
                    const orderableRow = jq("<div>").addClass("form-field-section row result-row align-items-start");
                    resultSection.append(orderableRow);
                    const testNameSection = jq("<span>").addClass("test-name col-3").append(concept.displayStringForLab);
                    orderableRow.append(testNameSection);

                    const widgetSection = jq("<span>").addClass("form-field-widgets col-6");
                    const widgetInfoSection = jq("<span>").addClass("form-field-widgets col-3");
                    orderableRow.append(widgetSection);
                    orderableRow.append(widgetInfoSection);

                    // TODO: Handle multiple results for same concept
                    const widget = formHelper.createObsWidget(concept, {
                        id: id + concept.uuid,
                        orderUuid: order.uuid,
                        groupingConceptUuid: isPanel ? orderable.uuid : null
                    });

                    const widgetWrapper = jq("<div>").addClass("lab-results-widget").append(widget);
                    widgetSection.append(widgetWrapper);

                    const referenceRangeSection = jq("<span>").addClass("reference-range");
                    if (concept.datatype.name === 'Numeric') {
                        const refRangeRep = "hiNormal,hiAbsolute,hiCritical,lowNormal,lowAbsolute,lowCritical";
                        const refRangeQuery = "patient=" + order.patient.uuid + "&encounter=" + order.encounter.uuid + "&concept=" + concept.uuid;
                        jq.get(openmrsContextPath + "/ws/rest/v1/conceptreferencerange?" + refRangeQuery + "&v=custom:(" + refRangeRep + ")", function (data) {
                            const refRange = data.results && data.results.length > 0 ? data.results[0] : null;
                            widgetSection.find(".result-numeric-input").on("blur", (event) => {
                                const textbox = jq(event.target);
                                const validationError = validateNumericResult(messages, concept, refRange, textbox.val());
                                const fieldErrorDiv = textbox.siblings(".field-error");
                                fieldErrorDiv.html("").removeClass("abnormal-value").removeClass("critical-value").removeClass("error-value");
                                if (validationError) {
                                    fieldErrorDiv.html(validationError.message).addClass(validationError.type + "-value");
                                }
                            }).blur();
                            const refRangeDisplay =
                                !refRange ? "" :
                                    refRange.lowNormal && refRange.hiNormal ? (refRange.lowNormal + " - " + refRange.hiNormal) :
                                        refRange.lowNormal ? (">= " + refRange.lowNormal) :
                                            refRange.hiNormal ? ("=< " + refRange.hiNormal) : "";
                            referenceRangeSection.html(refRangeDisplay);
                        });
                    }
                    widgetInfoSection.append(jq("<p>").append(referenceRangeSection));
                });

                const saveButton = parentElement.find(".action-button.confirm");
                saveButton.off("click");
                saveButton.on("click", (event) => {

                    event.preventDefault();
                    parentElement.find(".action-button").attr("disabled", "disabled");
                    parentElement.find(".errors-section").html("");

                    const encounterToSubmit = formHelper.constructEncounterPayload();
                    const fulfillerStatusToSubmit = jq("#" + id + "-fulfiller-status").val();
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
                        const encounterDate = moment(encounterToSubmit.encounterDatetime);
                        if (encounterDate.isAfter(resultDate, 'day')) {
                            errors.push(messages.resultDateCannotBeBeforeSpecimenDate);
                        }
                    }

                    const fieldErrors = jq(".field-error.error-value").get().map(element => jq(element).html().trim()).filter(Boolean);
                    if (fieldErrors.length > 0) {
                        errors.push(messages.errorsWithOneOrMoreFields);
                    }

                    if (errors && errors.length > 0) {
                        errors.forEach(e => {
                            parentElement.find(".errors-section").append(jq("<div>").html(e));
                        });
                        jq(".action-button").removeAttr("disabled");
                        return;
                    }

                    const payload = { encounter: encounterToSubmit, orders: ordersToSubmit };
                    jq.ajax({
                        url: openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders/" + encounterToSubmit.uuid,
                        type: "POST",
                        contentType: "application/json; charset=utf-8",
                        data: JSON.stringify(payload),
                        dataType: "json",
                        success: () => {
                            onSuccessFunction();
                            parentElement.find(".action-button").removeAttr("disabled");
                        },
                        error: (xhr) => {
                            parentElement.find(".action-button").removeAttr("disabled");
                            const error = xhr?.responseJSON?.error ?? xhr?.responseJSON;
                            const message = error?.translatedMessage ?? error.message ?? error;
                            parentElement.find(".errors-section").html(message);
                        }
                    });
                });

                parentElement.show();
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
        padding-left: 10px;
    }
    .field-error {
        display: inline;
        padding-left: 10px;
        font-weight: bold;
    }
    .field-error.abnormal-value {
        color: orange;
    }
    .field-error.critical-value {
        color: orange;
    }
    .field-error.error-value {
        color: red;
    }
</style>

<div id="${id}">
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
    </div>

    <br><br>
    <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
    <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
</div>