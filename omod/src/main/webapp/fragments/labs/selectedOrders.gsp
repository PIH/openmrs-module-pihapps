<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")
%>
<script type="text/javascript">
    function initializeSelectedOrders({ orders, selectedOrderUuids, readOnly, pihAppsConfig, jqElement }) {
        moment.locale(window.sessionContext?.locale ?? 'en');
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);
        jqElement.html("").toggleClass("orders-readonly", !!readOnly);
        if (orders && orders.length > 0) {
            const showCheckboxes = selectedOrderUuids !== undefined;
            let headerRow = jq("<div>").addClass("row table-header");
            if (showCheckboxes) {
                headerRow.append(jq("<div>").addClass("col-1"));
            }
            headerRow.append(jq("<div>").addClass(showCheckboxes ? "col-4" : "col-4").html("${ ui.message("pihapps.labTest") }"));
            headerRow.append(jq("<div>").addClass(showCheckboxes ? "col-4" : "col-4").html("${ ui.message("pihapps.orderDate") }"));
            headerRow.append(jq("<div>").addClass(showCheckboxes ? "col-3" : "col-4").html("${ ui.message("pihapps.orderNumber") }"));
            jqElement.append(headerRow);
            orders.forEach((o) => {
                const urgency = o.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '';
                const labTest = urgency + o.concept.displayStringForLab;
                let row = jq("<div>").addClass("row order-select-row").attr("data-order-uuid", o.uuid);
                if (showCheckboxes) {
                    const isChecked = selectedOrderUuids.includes(o.uuid);
                    const checkbox = jq("<input>")
                        .attr("type", "checkbox")
                        .addClass("order-select-checkbox")
                        .attr("data-order-uuid", o.uuid)
                        .attr("aria-label", o.concept.displayStringForLab)
                        .prop("checked", isChecked)
                        .prop("disabled", !!readOnly);
                    row.append(jq("<div>").addClass("col-1").append(checkbox));
                    row.append(jq("<div>").addClass("col-4").html(labTest));
                    row.append(jq("<div>").addClass("col-4").html(dateUtils.formatDateWithTimeIfPresent(o.dateActivated)));
                    row.append(jq("<div>").addClass("col-3").html(o.orderNumber));
                } else {
                    row.append(jq("<div>").addClass("col-4").html(labTest));
                    row.append(jq("<div>").addClass("col-4").html(dateUtils.formatDateWithTimeIfPresent(o.dateActivated)));
                    row.append(jq("<div>").addClass("col-4").html(o.orderNumber));
                }
                jqElement.append(row);
            });
        }
    }
</script>
<div class="orders-section" class="form-field-section row">
    <span class="orders-widgets" class="form-field-widgets col-8">

    </span>
</div>