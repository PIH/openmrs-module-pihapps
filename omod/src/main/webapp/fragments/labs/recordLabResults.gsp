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
        margin-left: 20px;
    }
    .order-section {
        border: 1px solid black;
        margin: 20px;
        padding: 10px;
    }
    .panel-test-row > .order-info {
        margin-left: 20px;
    }
    .result-numeric-input {
        display: inline-block;
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

        const ordersWidgetsSection = parentElement.find(".result-entry-form-results");
        ordersWidgetsSection.empty();

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

        const createResultsWidget = function(id, name, concept) {
            const wrapper = jq("<p>").attr("id", id).addClass("lab-results-widget left");
            if (concept.answers && concept.answers.length > 0) {
                const dropdown = jq("<select>").attr("id", id + "-field").attr("name", name);
                dropdown.append(jq("<option>").attr("value", "").html(""));
                concept.answers.forEach((a) => {
                   dropdown.append(jq("<option>").attr("value", a.uuid).html(a.displayStringForLab));
                });
                wrapper.append(dropdown);
            }
            else if (concept.datatype.name === "Numeric") {
                const textbox = jq("<input>").addClass("result-numeric-input").attr("type", "number").attr("id", id + "field").attr("name", name).attr("size", "10");
                wrapper.append(textbox);
                wrapper.append(jq("<span>").addClass("result-units").html(concept.units ?? ""));
                textbox.on("blur", () => {
                   const validationError = validateInput(concept, textbox.val());
                   textbox.siblings(".field-error").html(validationError ?? "");
                });
            }
            else if (concept.datatype.name === "Text") {
                const textbox = jq("<input>").addClass("result-text-input").attr("type", "text").attr("id", id + "field").attr("name", name).attr("size", "30");
                wrapper.append(textbox);
            }
            else {
                return "ERROR";
            }
            wrapper.append(jq("<div>").addClass("field-error"))
            return wrapper;
        }

        orders.forEach((order) => {
            const orderRow = jq("<div>").addClass("order-section");
            const baseConceptRep = "uuid,displayStringForLab,datatype:(uuid,name),conceptClass:(uuid,name),set,allowDecimal,units";
            const testRep = baseConceptRep + ",answers:(" + baseConceptRep + "),setMembers:(" + baseConceptRep + ")";
            jq.get(openmrsContextPath + "/ws/rest/v1/concept/" + order.concept.uuid + "?v=custom:(" + testRep + ")", function (orderable) {
                const orderableRow = jq("<div>").addClass("form-field-section row align-items-start");
                const orderInfo = jq("<span>").addClass("form-field-label order-info col-auto").append(orderable.displayStringForLab);
                const nonPanelTestWidgets = jq("<span>").addClass("form-field-widgets col");
                if (orderable.setMembers.length === 0) {
                    orderableRow.addClass("test-row");
                    const id = "result-" + orderable.uuid;
                    nonPanelTestWidgets.append(createResultsWidget(id, id, orderable));
                } else {
                    orderableRow.addClass("panel-row");
                }
                orderableRow.append(orderInfo);
                orderableRow.append(nonPanelTestWidgets);
                orderRow.append(orderableRow);
                orderable.setMembers.forEach((test, index) => {
                    const testRow = jq("<div>").addClass("panel-test-row test-row form-field-section row align-items-start");
                    const testInfo = jq("<span>").addClass("form-field-label order-info col-auto").append(test.displayStringForLab);
                    const id = "result-" + test.uuid;
                    const testWidgets = jq("<span>").addClass("form-field-widgets col").append(createResultsWidget(id, id, test));
                    if (index % 2 === 0) {
                        testRow.addClass("even");
                    }
                    testRow.append(testInfo).append(testWidgets);
                    orderRow.append(testRow);
                });
            });

            ordersWidgetsSection.append(orderRow);
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
                <span class="form-field-widgets col-auto">
                    ${ui.includeFragment("pihapps", "field/datetimepicker", [
                            id: id+"result-date-picker",
                            label: "",
                            formFieldName: "result_date",
                            useTime: true,
                            left: true,
                            defaultDate: new Date()
                    ])}
                </span>
            </div>
        </div>
        <div class="result-entry-form-results"></div>
        <br><br>
        <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
        <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
    </form>
</div>