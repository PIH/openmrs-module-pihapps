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

        const orders = formConfig.orders;
        const pihAppsConfig = formConfig.pihAppsConfig;
        const onSuccessFunction = formConfig.onSuccessFunction;

        if (!orders || orders.length === 0) {
            return;
        }

        moment.locale(window.sessionContext?.locale ?? 'en');
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);

        const ordersWidgetsSection = parentElement.find(".orders-widgets");
        initializeSelectedOrders({orders: orders, pihAppsConfig: pihAppsConfig, jqElement: ordersWidgetsSection});

        parentElement.show();
    }
</script>

<div id="${id}">
    <div class="errors-section"></div>
    <form>
        <div class="dialog-content form">
            ${ ui.includeFragment("pihapps", "labs/selectedOrders") }
        </div>

        <br><br>
        <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
        <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
    </form>
</div>