<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
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
        };

        const fulfillerEncounter = order.fulfillerEncounter;

        if (!order || !fulfillerEncounter) {
            console.warn("recordLabResults requires an order that has a non-null fulfillerEncounter")
            return;
        }

        const locale = window.sessionContext?.locale ?? 'en';
        moment.locale(locale);
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);
        const currentDatetime = dateUtils.roundDownToNearestMinuteInterval(new Date(), 5);

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);

        // Populate top sections containing specimen and order details

        const orderAndSpecimenSection = parentElement.find(".order-and-specimen-section");
        const encounterRole = pihAppsConfig.labOrderConfig.specimenCollectionEncounterRole?.uuid;
        if (!encounterRole) {
            parentElement.find(".specimen-provider-section").hide();
        }

        const specimenEncounterRep = "uuid,encounterDatetime,location:(uuid,display),encounterProviders:(provider:(uuid,display)),obs:(concept:(uuid),valueCoded:(uuid,display),valueDatetime,valueText)";
        jq.get(openmrsContextPath + "/ws/rest/v1/encounter/" + fulfillerEncounter.uuid + "?v=custom:(" + specimenEncounterRep + ")", function (e) {
            const labId = e.obs.find((o) => o.concept.uuid === pihAppsConfig.labOrderConfig.labIdentifierConcept.uuid)?.valueText;
            const estimatedObs = e.obs.find((o) => o.concept.uuid === pihAppsConfig.labOrderConfig.estimatedCollectionDateQuestion.uuid);
            const estimated = estimatedObs?.valueCoded?.uuid === pihAppsConfig.labOrderConfig.estimatedCollectionDateAnswer.uuid;
            const receivedDate = e.obs.find((o) => o.concept.uuid === pihAppsConfig.labOrderConfig.specimenReceivedDateQuestion.uuid)?.valueDatetime;
            const testLocation = e.obs.find((o) => o.concept.uuid === pihAppsConfig.labOrderConfig.testLocationQuestion.uuid)?.valueCoded?.display;
            const providers = e.encounterProviders?.map((p) => p.display).join(", ");
            const estimatedText = estimated ? '<span class="estimated-text">(' + messages.estimated + ")</span>" : "";

            orderAndSpecimenSection.find(".lab-id").html(labId);
            orderAndSpecimenSection.find(".specimen-collection-date").html(dateUtils.formatDateWithTimeIfPresent(e.encounterDatetime) + estimatedText);
            orderAndSpecimenSection.find(".specimen-collection-location").html(e.location?.display);
            orderAndSpecimenSection.find(".specimen-received-date").html(dateUtils.formatDateWithTimeIfPresent(receivedDate));
            orderAndSpecimenSection.find(".lab-test-location").html(testLocation);
            if (providers) {
                orderAndSpecimenSection.find(".specimen-collected-by").html(providers);
            }
            else {
                orderAndSpecimenSection.find(".specimen-collected-by").parent().hide();
            }
        });

        orderAndSpecimenSection.find(".orderable").html(order.concept.displayStringForLab);
        orderAndSpecimenSection.find(".order-date").html(dateUtils.formatDateWithTimeIfPresent(order.dateActivated));
        orderAndSpecimenSection.find(".test-ordered-by").html(order.orderer?.display);
        orderAndSpecimenSection.find(".order-number").html(order.orderNumber);
        orderAndSpecimenSection.find(".order-location").html(order.encounter.location?.display);

        // Populate results form with widgets and initial values

        // Remove any previously populated widgets/fields
        const resultsEntrySection = parentElement.find(".result-entry-section");
        resultsEntrySection.find(".result-field").empty();
        resultsEntrySection.find(".result-row").remove();

        // Add result date and fulfiller status widgets
        const fulfillerStatusSection = resultsEntrySection.find(".fulfillerStatus");
        const fulfillerStatusWidget = jq("<select>").attr("id", id + "-fulfiller-status");
        if (!order.fulfillerStatus) {
            fulfillerStatusWidget.append(jq("<option>").attr("value", ""));
        }
        const supportedFulfillerStatuses = ["IN_PROGRESS", "COMPLETED", "EXCEPTION"];
        pihAppsConfig.labOrderConfig.fulfillerStatusOptions.filter((option) => supportedFulfillerStatuses.includes(option.status)).forEach((option) => {
           fulfillerStatusWidget.append(jq("<option>").attr("value", option.status).html(option.display));
        });
        fulfillerStatusWidget.val(order.fulfillerStatus);
        fulfillerStatusSection.append(fulfillerStatusWidget);

        const resultDateSection = resultsEntrySection.find(".resultDate");
        resultDateSection.append(dateUtils.createDatePickerWidget(jq, {
            id: id + "-result-date",
            locale: locale,
        }));

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

        const createResultWidget = function(order, concept, resultNum) {
            const widget = jq("<span>").addClass("result-widget");
            const widgetType = (concept.answers && concept.answers.length > 0) ? "select" : "input";
            const widgetField = jq("<" + widgetType + ">");
            widgetField.addClass("result-value-field")
            widgetField.attr("data-order-uuid", order.uuid);
            widgetField.attr("data-order-concept-uuid", order.concept.uuid);
            widgetField.attr("data-concept-uuid", concept.uuid);
            widgetField.attr("data-result-num", resultNum);
            widgetField.attr("name", "result-value");
            widget.append(widgetField);

            if (widgetType === "select") {
                widgetField.append(jq("<option>").attr("value", "").html(""));
                concept.answers.forEach((a) => {
                    widgetField.append(jq("<option>").attr("value", a.uuid).html(a.display));
                });
            }
            else if (concept.datatype.name === "Numeric") {
                widgetField.addClass("result-numeric-input").attr("type", "number").attr("size", "10");
                widget.append(jq("<span>").addClass("result-units").html(concept.units ?? ""));
            }
            else if (concept.datatype.name === "Text") {
                widgetField.addClass("result-text-input").attr("type", "text").attr("size", "30");
            }
            else {
                widget.append("Unable to handle concept of type: " + concept.datatype.name);
            }
            widget.append(jq("<div>").addClass("field-error"));
            return widget;
        }

        // Add result widgets
        const resultSection = resultsEntrySection.find(".result-fields");
        const baseConceptRep = "uuid,display,displayStringForLab,datatype:(uuid,name),conceptClass:(uuid,name),set,allowDecimal,units";
        const baseConceptRepWithAnswers = baseConceptRep + ",answers:(" + baseConceptRep + ")";
        const testRep = baseConceptRepWithAnswers + ",setMembers:(" + baseConceptRepWithAnswers + ")";
        jq.get(openmrsContextPath + "/ws/rest/v1/concept/" + order.concept.uuid + "?v=custom:(" + testRep + ")", function (orderable) {
            const tests = orderable.setMembers.length === 0 ? [orderable] : orderable.setMembers;
            tests.forEach((concept) => {
                const orderableRow = jq("<div>").addClass("form-field-section row result-row align-items-start");
                resultSection.append(orderableRow);
                const testNameSection = jq("<span>").addClass("test-name col-3").append(concept.displayStringForLab);
                orderableRow.append(testNameSection);

                const widgetSection = jq("<span>").addClass("form-field-widgets col-3");
                const widgetInfoSection = jq("<span>").addClass("form-field-widgets col-auto");
                orderableRow.append(widgetSection);
                orderableRow.append(widgetInfoSection);

                const widget = createResultWidget(order, concept, 0);
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
                            fieldErrorDiv.html("").removeClass("abnormal-value").removeClass("critical-value").removeClass("error");
                            if (validationError) {
                                fieldErrorDiv.html(validationError.message).addClass(validationError.type + "-value");
                            }
                        });
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
        });

        const saveButton = jq("#save-button");
        saveButton.off("click");
        saveButton.on("click", (event) => {

            event.preventDefault();
            parentElement.find(".action-button").attr("disabled", "disabled");
            parentElement.find(".errors-section").html("");

            // Track any validation errors as data is processed
            const errors = [];
            const currentDate = moment();
            const obs = [];

            const fulfillerStatus = jq("#" + id + "-fulfiller-status").val();
            const resultDateStr = jq("#" + id + "-result-date-field").val();

            if (resultDateStr) {
                const resultDate = moment(resultDateStr);
                if (resultDate.isAfter(currentDate)) {
                    errors.push(messages.resultDateCannotBeFuture);
                }
                const encounterDate = moment(fulfillerEncounter.encounterDatetime);
                if (encounterDate.isAfter(resultDate)) {
                    errors.push(messages.resultDateCannotBeBeforeSpecimenDate);
                }
                obs.push({
                    order: order.uuid,
                    concept: pihAppsConfig.labOrderConfig.resultsDateQuestion.uuid,
                    value: dateUtils.formatDateWithTimeIfPresent(resultDate),
                    formNamespaceAndPath: 'pihapps^result-date',
                    comments: "result-entry-form^result-date" // For backwards-compatibility with lab workflow
                });
            }

            const resultElements = jq(".result-value-field");
            resultElements.each((index, element) => {
                const value = jq(element).val().trim();
                const data = jq(element).data();
                const concept = data.conceptUuid;
                const orderable = data.orderConceptUuid;
                const resultNum = data.resultNum;
                const formPath = orderable + (orderable === concept ? "" : "^" + concept) + (resultNum === 0 ? "" : "_" + resultNum);
                if (value) {
                    const labResultObs = {
                        order: order.uuid,
                        concept: concept,
                        value: value,
                        formNamespaceAndPath: 'pihapps^' + formPath,
                        comments: "result-entry-form^" + formPath // For backwards-compatibility with lab workflow
                    }
                    if (orderable === concept) {
                        obs.push(labResultObs);
                    }
                    else {
                        let obsGroup = obs.find((o) => o.concept === orderable);
                        if (!obsGroup) {
                            obsGroup = {
                                order: order.uuid,
                                concept: orderable,
                                formNamespaceAndPath: 'pihapps^' + formPath,
                                comments: "result-entry-form^" + formPath,
                                groupMembers: []
                            }
                            obs.push(obsGroup);
                        }
                        obsGroup.groupMembers.push(labResultObs);
                    }
                }
            });

            console.log(fulfillerStatus, obs);
            parentElement.find(".action-button").removeAttr("disabled");

            onSuccessFunction();
        });

        parentElement.show();
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
    }
    .result-units {
        padding-left: 10px;
    }
    .field-error.abnormal-value {
        color: orange;
    }
    .field-error.critical-value {
        color: orange;
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
        <div class="result-section">
            <fieldset>
                <legend>${ ui.message("pihapps.results") }</legend>
                <div class="result-fields">
                    <div class="row align-items-start result-field-labels">
                        <span class="col-3 result-field-label">${ ui.message("pihapps.labTest") }</span>
                        <span class="col-3 result-field-label">${ ui.message("pihapps.result") }</span>
                        <span class="col-auto result-field-label">${ ui.message("pihapps.normalRange") }</span>
                    </div>
                </div>
            </fieldset>
        </div>
        <div class="result-status-section">
            <fieldset>
                <legend>${ ui.message("pihapps.status") }</legend>
                <div class="row status-row align-items-start">
                    <span class="col-3 result-field-label">${ ui.message("pihapps.status") }</span>
                    <span class="col-auto result-field fulfillerStatus"></span>
                </div>
                <div class="row status-row align-items-start">
                    <span class="col-3 result-field-label">${ ui.message("pihapps.resultDate") }</span>
                    <span class="col-auto result-field resultDate"></span>
                </div>
            </fieldset>
        </div>
    </div>

    <br><br>
    <button id="cancel-button" class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
    <button id="save-button" class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
</div>