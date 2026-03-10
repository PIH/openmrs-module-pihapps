<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "conceptUtils.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def now = new Date()

    config.require("id")
    def id = config.id
%>

<script type="text/javascript">
    function initializeOrderNotFulfilledForm(formConfig) {

        const patientUuid = formConfig.patientUuid;
        const orders = formConfig.orders;
        const pihAppsConfig = formConfig.pihAppsConfig;
        const onSuccessFunction = formConfig.onSuccessFunction;

        if (!orders || orders.length === 0) {
            return;
        }

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);

        const ordersWidgetsSection = parentElement.find(".orders-widgets");
        initializeSelectedOrders({ orders: orders, pihAppsConfig: pihAppsConfig, jqElement: ordersWidgetsSection});

        const reasonPicker = jq(selectorPrefix + "remove-reason-picker-field");
        reasonPicker.empty();
        pihAppsConfig.labOrderConfig.reasonTestNotPerformedQuestion?.answers?.forEach((answer) => {
            reasonPicker.append(jq("<option>").attr("value", answer.uuid).html(answer.display));
        });

        // Populate default values each time form is opened
        parentElement.find(".errors-section").html("");
        parentElement.find(":input").val("");

        const formElement = parentElement.find("form");
        formElement.off("submit");
        formElement.on("submit", (event) => {

            event.preventDefault();
            parentElement.find(".action-button").attr("disabled", "disabled");
            const formData = formElement.serializeArray();
            const errorsSection = parentElement.find(".errors-section");

            errorsSection.html("");
            const removeReason = formData.find(e => e.name === "remove-reason-dropdown")?.value ?? ''
            if (!removeReason) {
                errorsSection.append(jq("<div>").html('${ ui.message("pihapps.reasonRequired") }'));
                jq(".action-button").removeAttr("disabled");
                return;
            }

            const removeOrdersPayload = {
                "orders": orders.map(o => o.uuid),
                "reason": removeReason
            }
            jq.ajax({
                url: openmrsContextPath + "/ws/rest/v1/pihapps/markOrdersAsNotPerformed",
                type: "POST",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify(removeOrdersPayload),
                dataType: "json",
                success: () => {
                    onSuccessFunction();
                },
                error: (xhr) => {
                    jq(".action-button").removeAttr("disabled");
                    const error = xhr?.responseJSON?.error;
                    const message = error?.translatedMessage ?? error.message ?? error;
                    errorsSection.html(message);
                }
            });
        });

        parentElement.show();
    }
</script>

<div id="${id}">
    <div class="form-header">
        ${ui.message("pihapps.removeSelectedOrders")}
    </div>
    <div class="errors-section"></div>
    <form>
        <div class="dialog-content form">
            ${ ui.includeFragment("pihapps", "labs/selectedOrders") }
            <div class="remove-reason-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.reason")}:</span>
                <span class="form-field-widgets col-auto">
                    ${ui.includeFragment("uicommons", "field/dropDown", [
                            id: id + "remove-reason-picker",
                            label: "",
                            formFieldName: "remove-reason-dropdown",
                            left: true,
                            options: [],
                            initialValue: ""
                    ])}
                </span>
            </div>
            <br><br>
            <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
            <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
        </div>
    </form>
</div>