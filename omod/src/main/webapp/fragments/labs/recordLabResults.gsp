<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")

    config.require("id")
    def id = config.id
%>

<style>
    .result-date-section {
        padding-left: 20px;
    }
    .order-section {
        border-bottom: 1px solid black;
        margin: 10px;
        padding: 5px;
    }
    .results-header {
        border-bottom: 1px solid black;
        margin: 20px;
        padding: 10px;
        font-weight: bold;
    }
    .test-name {
        padding-left: 50px;
    }
    .test-name.panel-name {
        padding-left: 20px
    }
    .result-numeric-input {
        display: inline-block;
        min-width: unset;
    }
    form input, form select, form textarea, form ul.select, .form input, .form select, .form textarea, .form ul.select {
        min-width: unset;
        padding-top: 2px;
        padding-bottom: 2px;
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

<script type="text/javascript">
    function initializeLabResultsForm(formConfig) {

        const orders = formConfig.orders;
        const pihAppsConfig = formConfig.pihAppsConfig;
        const onSuccessFunction = formConfig.onSuccessFunction;

        const messages = {
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

        if (!orders || orders.length === 0) {
            return;
        }

        const locale = window.sessionContext?.locale ?? 'en';
        moment.locale(locale);
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);
        const currentDatetime = dateUtils.roundDownToNearestMinuteInterval(new Date(), 5);

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);

        const resultsEntrySection = parentElement.find(".result-entry-form-results");
        resultsEntrySection.find(".order-section").remove();

        const resultDate = currentDatetime;
        jq(selectorPrefix + "result-date-picker-wrapper").datetimepicker("option", "maxDateTime", currentDatetime);
        jq(selectorPrefix + "result-date-picker-wrapper").datetimepicker("setDate", resultDate);

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

        const addResultRow = function(orderRow, order, concept) {
            const orderableRow = jq("<div>").addClass("form-field-section row orderable-row align-items-start");
            orderRow.append(orderableRow);
            const orderInfo = jq("<span>").addClass("form-field-label test-name col-3").append(concept.displayStringForLab);
            orderableRow.append(orderInfo);

            const dateSection = jq("<span>").addClass("form-field-date col-3");
            if (order.concept.uuid === concept.uuid) {
                const datePickerWidget = dateUtils.createDatePickerWidget(jq, {
                    id: id + "-date-picker-" + order.uuid,
                    locale: locale,
                    initialValue: currentDatetime
                });
                dateSection.append(datePickerWidget);
            }
            orderableRow.append(dateSection);

            const widgetSection = jq("<span>").addClass("form-field-widgets col-3");
            const widgetInfoSection = jq("<span>").addClass("form-field-widgets col-auto");
            orderableRow.append(widgetSection);
            orderableRow.append(widgetInfoSection);

            const widget = createResultWidget(order, concept, 0);
            const widgetWrapper = jq("<p>").addClass("lab-results-widget left").append(widget);
            widgetSection.append(widgetWrapper);

            const referenceRangeSection = jq("<span>").addClass("reference-range form-field-label");
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
            widgetInfoSection.append(jq("<p>").append(referenceRangeSection));
        }

        orders.forEach((order) => {
            const orderRow = jq("<div>").addClass("order-section");
            const baseConceptRep = "uuid,display,displayStringForLab,datatype:(uuid,name),conceptClass:(uuid,name),set,allowDecimal,units";
            const baseConceptRepWithAnswers = baseConceptRep + ",answers:(" + baseConceptRep + ")";
            const testRep = baseConceptRepWithAnswers + ",setMembers:(" + baseConceptRepWithAnswers + ")";
            jq.get(openmrsContextPath + "/ws/rest/v1/concept/" + order.concept.uuid + "?v=custom:(" + testRep + ")", function (orderable) {
                if (orderable.setMembers.length === 0) {
                    addResultRow(orderRow, order, orderable);
                }
                else {
                    const row = jq("<div>").addClass("row align-items-start");
                    row.append(jq("<span>").addClass("form-field-label panel-name test-name col-3").append(orderable.displayStringForLab));
                    const dateSection = jq("<span>").addClass("form-field-date col-3");
                    dateSection.append(dateUtils.createDatePickerWidget(jq, {
                        id: id + "-date-picker-" + order.uuid,
                        locale: locale,
                        initialValue: currentDatetime
                    }));
                    row.append(dateSection);
                    row.append(jq("<span>").addClass("col-auto"));
                    orderRow.append(row);
                    orderable.setMembers.forEach((test) => {
                        addResultRow(orderRow, order, test);
                    })
                }
            });
            resultsEntrySection.append(orderRow);
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

            // TODO: Handle voiding and editing existing data

            const resultObs = [];
            const dateObs = [];
            const resultElements = jq(".result-value-field");
            resultElements.each((index, element) => {
                const value = jq(element).val().trim();
                const data = jq(element).data();
                const orderUuid = data.orderUuid;
                const concept = data.conceptUuid;
                const orderable = data.orderConceptUuid;
                const resultNum = data.resultNum;
                const formPath = orderable + (orderable === concept ? "" : "^" + concept) + (resultNum === 0 ? "" : "_" + resultNum);
                const order = orders.find((o) => o.uuid === orderUuid);
                if (value) {

                    if (!order.fulfillerEncounter) {
                        errors.push(messages.resultsMustHaveAssociatedSpecimenEncounter);
                    }
                    else {
                        const resultDateStr = jq("#" + id + "-date-picker-" + orderUuid + "-field").val();
                        if (resultDateStr) {
                            const resultDate = moment(resultDateStr);
                            const encounterDate = moment(order.fulfillerEncounter.encounterDatetime);
                            if (encounterDate.isAfter(resultDate)) {
                                errors.push(messages.resultDateCannotBeBeforeSpecimenDate);
                            }
                        }
                    }

                    const obs = {
                        order: orderUuid,
                        person: order.patient.uuid,
                        encounter: order.encounter.uuid,
                        concept: concept,
                        value: value,
                        formNamespaceAndPath: 'pihapps^' + formPath,
                        comments: "result-entry-form^" + formPath // For backwards-compatibility with lab workflow
                    }
                    // Single tests
                    if (orderable === concept) {
                        resultObs.push(obs);
                    }
                    else {
                        let obsGroup = resultObs.find((o) => o.concept === orderable);
                        if (!obsGroup) {
                            obsGroup = {
                                order: orderUuid,
                                person: order.patient.uuid,
                                encounter: order.fulfillerEncounter?.uuid,
                                concept: orderable,
                                formNamespaceAndPath: 'pihapps^' + formPath,
                                comments: "result-entry-form^" + formPath,
                                groupMembers: []
                            }
                            resultObs.push(obsGroup);
                        }
                        obsGroup.groupMembers.push(obs);
                    }
                }
            });

            // For each order that has result obs, create a date obs if populated
            resultObs.forEach((obs) => {
                const resultDateStr = jq("#" + id + "-date-picker-" + obs.order + "-field").val();
                if (resultDateStr) {
                    const resultDate = moment(resultDateStr);
                    if (resultDate.isAfter(currentDate)) {
                        errors.push(messages.resultDateCannotBeFuture);
                    }
                    dateObs.push({
                        order: obs.order,
                        person: obs.person,
                        encounter: obs.encounter,
                        concept: pihAppsConfig.labOrderConfig.resultsDateQuestion.uuid,
                        value: dateUtils.formatDateWithTimeIfPresent(resultDate),
                        formNamespaceAndPath: 'pihapps^result-date',
                        comments: "result-entry-form^result-date" // For backwards-compatibility with lab workflow
                    });
                }
            });

            console.log(resultObs, dateObs, errors);
            onSuccessFunction();
        });

        parentElement.show();
    }
</script>

<div id="${id}">
    <div class="errors-section"></div>
    <div class="result-entry-form-results">
        <div class="row results-header align-items-start">
            <span class="col-3">${ ui.message("pihapps.labTest") }</span>
            <span class="col-3">${ ui.message("pihapps.resultDate") }</span>
            <span class="col-3">${ ui.message("pihapps.results") }</span>
            <span class="col-auto">${ ui.message("pihapps.normalRange") }</span>
        </div>
    </div>
    <br><br>
    <button id="cancel-button" class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
    <button id="save-button" class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
</div>