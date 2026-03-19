<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")
%>
<script type="text/javascript">
    function initializeSelectedOrders({ orders, pihAppsConfig, jqElement }) {
        moment.locale(window.sessionContext?.locale ?? 'en');
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);
        jqElement.html("");
        if (orders && orders.length > 0) {
            // Display the orders that are included at the top of the form, read-only
            let headerRow = jq("<div>").addClass("row table-header");
            headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.labTest") }"));
            headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.orderDate") }"));
            headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.orderNumber") }"));
            jqElement.append(headerRow);
            orders.forEach((o) => {
                const urgency = o.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '';
                const labTest = urgency + o.concept.displayStringForLab;
                let row = jq("<div>").addClass("row");
                row.append(jq("<div>").addClass("col-4").html(labTest));
                row.append(jq("<div>").addClass("col-4").html(dateUtils.formatDateWithTimeIfPresent(o.dateActivated)));
                row.append(jq("<div>").addClass("col-4").html(o.orderNumber));
                jqElement.append(row);
            });
        }
    }
</script>
<div class="orders-section" class="form-field-section row">
    <span class="orders-widgets" class="form-field-widgets col-8">

    </span>
</div>