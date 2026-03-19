<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeJavascript("pihapps", "formHelper.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def now = new Date()

    config.require("id")
    def id = config.id
%>

<script type="text/javascript">
    function initializeOrderNotFulfilledForm(formConfig) {

        const orders = formConfig.orders;
        const reason = formConfig.reason;
        const pihAppsConfig = formConfig.pihAppsConfig;
        const onSuccessFunction = formConfig.onSuccessFunction;

        if (!orders || orders.length === 0) {
            return;
        }

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);

        const locale = window.sessionContext?.locale ?? 'en';
        moment.locale(locale);

        const ordersWidgetsSection = parentElement.find(".orders-widgets");
        initializeSelectedOrders({ orders: orders, pihAppsConfig: pihAppsConfig, jqElement: ordersWidgetsSection});

        const formHelper = new FormHelper({
            jq: jq,
            moment: moment,
            locale: locale,
            dateFormat: pihAppsConfig.dateFormat,
            dateTimeFormat: pihAppsConfig.dateTimeFormat,
            formName: "pihapps^orderNotFulfilled",
        });

        const reasonQuestion = pihAppsConfig.labOrderConfig.reasonTestNotPerformedQuestion;

        const reasonPicker = formHelper.createSelectWidget({
            id: id + "remove-reason-picker",
            options: reasonQuestion?.answers?.map(a => { return {value: a.uuid, display: a.display }}),
            initialValue: reason?.valueCoded?.uuid ?? ""
        });
        parentElement.find(".obs-field-remove-reason").empty().append(reasonPicker);

        // Populate default values each time form is opened
        parentElement.find(".errors-section").html("");

        const saveButton = parentElement.find(".action-button.confirm");
        saveButton.off("click");
        saveButton.on("click", (event) => {

            event.preventDefault();
            parentElement.find(".action-button").attr("disabled", "disabled");
            const errorsSection = parentElement.find(".errors-section");

            errorsSection.html("");
            const removeReason = reasonPicker.val();
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
                    parentElement.find(".action-button").removeAttr("disabled");
                },
                error: (xhr) => {
                    parentElement.find(".action-button").removeAttr("disabled");
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
                    <span class="obs-field-remove-reason"></span>
                </span>
            </div>
            <br><br>
            <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
            <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
        </div>
    </form>
</div>