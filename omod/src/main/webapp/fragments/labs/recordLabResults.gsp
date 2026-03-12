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
</style>

<script type="text/javascript">
    function initializeLabResultsForm(formConfig) {

        const orders = formConfig.orders;
        const pihAppsConfig = formConfig.pihAppsConfig;
        const onSuccessFunction = formConfig.onSuccessFunction;

        if (!orders || orders.length === 0) {
            return;
        }

        moment.locale(window.sessionContext?.locale ?? 'en');
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

        // Returns a validation error code if invalid, null if valid
        const validateInput = function(concept, value) {
            if (value) {
                if (!concept.allowDecimal && !Number.isInteger(+value)) {
                    return "pihapps.resultMustBeInteger";
                }
                // TODO: Reference ranges
            }
            return null;
        }

        const addResultRow = function(orderRow, order, concept, indexInPanel) {
            const id = "result-" + concept.uuid;
            const orderableRow = jq("<div>").addClass("form-field-section row orderable-row align-items-start");
            orderRow.append(orderableRow);
            const orderInfo = jq("<span>").addClass("form-field-label test-name col-4").append(concept.displayStringForLab);
            orderableRow.append(orderInfo);
            const widgetSection = jq("<span>").addClass("form-field-widgets col-4");
            const widgetInfoSection = jq("<span>").addClass("form-field-widgets col-auto");
            orderableRow.append(widgetSection);
            orderableRow.append(widgetInfoSection);

            const wrapper = jq("<p>").attr("id", id).addClass("lab-results-widget left");
            if (concept.answers && concept.answers.length > 0) {
                const dropdown = jq("<select>").attr("id", id + "-field").attr("name", name);
                dropdown.append(jq("<option>").attr("value", "").html(""));
                concept.answers.forEach((a) => {
                   dropdown.append(jq("<option>").attr("value", a.uuid).html(a.display));
                });
                wrapper.append(dropdown);
            }
            else if (concept.datatype.name === "Numeric") {
                const textbox = jq("<input>").addClass("result-numeric-input").attr("type", "number").attr("id", id + "field").attr("name", name).attr("size", "10");
                wrapper.append(textbox);
                wrapper.append(jq("<span>").addClass("result-units").html(concept.units ?? ""));
                const referenceRangeSection = jq("<span>").addClass("reference-range form-field-label");
                const refRangeRep = "hiNormal,hiAbsolute,hiCritical,lowNormal,lowAbsolute,lowCritical";
                const refRangeQuery = "patient=" + order.patient.uuid + "&encounter=" + order.encounter.uuid + "&concept=" + concept.uuid;
                jq.get(openmrsContextPath + "/ws/rest/v1/conceptreferencerange?" + refRangeQuery + "&v=custom:(" + refRangeRep + ")", function (data) {
                    const refRange = data.results && data.results.length > 0 ? data.results[0] : null;
                    const refRangeDisplay =
                            !refRange ? "" :
                            refRange.lowNormal && refRange.hiNormal ? (refRange.lowNormal + " - " + refRange.hiNormal) :
                            refRange.lowNormal ? (">= " + refRange.lowNormal) :
                            refRange.hiNormal ? ("=< " + refRange.hiNormal) : "";
                    referenceRangeSection.html(refRangeDisplay);
                });
                widgetInfoSection.append(jq("<p>").append(referenceRangeSection));
            }
            else if (concept.datatype.name === "Text") {
                const textbox = jq("<input>").addClass("result-text-input").attr("type", "text").attr("id", id + "field").attr("name", name).attr("size", "30");
                wrapper.append(textbox);
            }
            else {
                wrapper.append("Unable to handle concept of type: " + concept.datatype.name);
            }
            wrapper.append(jq("<div>").addClass("field-error"))
            widgetSection.append(wrapper);
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
                    const panelInfo = jq("<span>").addClass("form-field-label panel-name test-name col").append(orderable.displayStringForLab);
                    row.append(panelInfo);
                    orderRow.append(row);
                    orderable.setMembers.forEach((test, index) => {
                        addResultRow(orderRow, order, test, index);
                    })
                }
            });
            resultsEntrySection.append(orderRow);
        });
        parentElement.show();
    }
</script>

<div id="${id}">
    <div class="errors-section"></div>

    <form>
        <div class="dialog-content form result-entry-form">
            <div class="result-date-section form-field-section row align-items-start">
                <span class="form-field-label col-2">${ui.message("pihapps.resultDate")}:</span>
                <span class="form-field-widgets col-6">
                    ${ui.includeFragment("pihapps", "field/datetimepicker", [
                            id: id+"result-date-picker",
                            label: "",
                            formFieldName: "result_date",
                            useTime: true,
                            left: true,
                            defaultDate: new Date()
                    ])}
                </span>
                <span class="col-auto"></span>
            </div>
        </div>
        <div class="result-entry-form-results">
            <div class="row results-header align-items-start">
                <span class="col-4">${ ui.message("pihapps.labTest") }</span>
                <span class="col-4">${ ui.message("pihapps.results") }</span>
                <span class="col-auto">${ ui.message("pihapps.normalRange") }</span>
            </div>
        </div>
        <br><br>
        <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
        <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
    </form>
</div>