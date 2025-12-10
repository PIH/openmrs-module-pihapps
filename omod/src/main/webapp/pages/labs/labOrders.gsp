<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("pihapps", "labs/orderEntry.css")
    ui.includeCss("pihapps", "labs/labOrder.css")
    ui.includeJavascript("uicommons", "moment.min.js")
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.escapeJs(ui.format(patient.patient)) }" , link: '${ui.urlBind("/" + contextPath + pihAppsConfig.getDashboardUrl(), ["patientId": patient.id])}'},
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labOrders")) }" , link: '${ui.pageLink("pihapps", "labs/labOrders", ["patientId": patient.id])}'}
    ];
    function discontinueOrder(orderUuid, orderableUuid) {
        const discontinueDialog = emr.setupConfirmationDialog({
            selector: '#discontinue-order-dialog',
            actions: {
                confirm: function() {

                    const discontinueReason = jq("#discontinue-reason-field").val();
                    const discontinueDate = moment().format('YYYY-MM-DDTHH:mm:ss.SSS');
                    const patient = '${patient.patient.uuid}';
                    const orderer = '${sessionContext.currentProvider.uuid}';
                    jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config", function(pihAppsConfig) {
                        const labOrderConfig = pihAppsConfig.labOrderConfig;
                        const encounterPayload = {
                            patient: patient,
                            encounterType: labOrderConfig.labOrderEncounterType,
                            encounterDatetime: discontinueDate,
                            location: '${sessionContext.sessionLocation.uuid}',
                            encounterProviders: [ { encounterRole: labOrderConfig.labOrderEncounterRole, provider: orderer } ],
                            orders: [
                                {
                                    type: 'testorder',
                                    action: 'DISCONTINUE',
                                    previousOrder: orderUuid,
                                    patient: patient,
                                    orderer: orderer,
                                    orderType: '${labOrderType.uuid}',
                                    concept: orderableUuid,
                                    urgency: 'ROUTINE',
                                    orderReasonNonCoded: discontinueReason,
                                    careSetting: labOrderConfig.defaultCareSetting?.uuid,
                                    dateActivated: discontinueDate,
                                }
                            ]
                        };
                        jq.ajax({
                            url: openmrsContextPath + '/ws/rest/v1/encounter',
                            type: 'POST',
                            contentType: 'application/json; charset=utf-8',
                            data: JSON.stringify(encounterPayload),
                            dataType: 'json', // Expect JSON response
                            success: function(response) {
                                emr.successMessage('${ui.encodeJavaScript(ui.message("pihapps.discontinueSuccessMessage"))}');
                                document.location.href = '${ui.pageLink('pihapps', 'labs/labOrders', [patient: patient.id])}>';
                            },
                            error: function(xhr, status, error) {
                                const message = xhr.responseJSON?.error?.message ?? error ?? xhr.responseText;
                                emr.errorMessage('${ui.encodeJavaScript(ui.message("pihapps.discontinueErrorMessage"))}: ' + message);
                            }
                        });
                    })
                },
                cancel: function() {
                    jq("#discontinue-reason-field").val("")
                }
            }
        });
        discontinueDialog.show();
    }
    jq(document).ready(function() {
        jq(".test-filter").change(function() {
            jq("#test-filter-form").submit();
        });
    });
</script>

<style>
    .test-filter {
        min-width: unset;
    }
    .test-filter-section {
        margin-bottom: 10px;
    }
</style>

<div class="row justify-content-between">
    <div class="col-6">
        <h3>${ ui.message("pihapps.labOrders.active") }</h3>
    </div>
    <div class="col-6 text-right">
        <a href="${ui.pageLink("pihapps", "labs/labOrder", ["patient": patient.patient.uuid])}">
            <input type="button" value="${ui.message("pihapps.addLabOrders")}" style="max-width: unset;"/>
        </a>
    </div>
</div>
<form method="get" id="test-filter-form">
    <input type="hidden" name="patient" value="${patient.patient.uuid}"/>
    <div class="row test-filter-section">
        <div class="col text-right">
            <select name="testConcept" class="test-filter float-right">
                <option value="">${ ui.message("pihapps.allTests") }</option>
                <% testConcepts.forEach { c -> %>
                    <option value="${c.id}"${c == testConcept ? " selected" : ""}>${pihAppsUtils.formatLabTest(c)}</option>
                <% } %>
            </select>
        </div>
    </div>
</form>
<table id="active-orders-list" width="100%" border="1" cellspacing="0" cellpadding="2">
    <thead>
        <tr>
            <th>${ ui.message("pihapps.orderDate") }</th>
            <th>${ ui.message("pihapps.orderNumber") }</th>
            <th>${ ui.message("pihapps.labTest") }</th>
            <th>${ ui.message("pihapps.testOrderedBy") }</th>
            <th>${ ui.message("pihapps.status")} </th>
            <th></th>
        </tr>
    </thead>
    <tbody>
    <% if (activeOrders.isEmpty()) { %>
        <tr>
            <td colspan="6">${ ui.message("coreapps.none") }</td>
        </tr>
    <% } %>
    <% activeOrders.each { labOrder ->
        def status = labOrder.fulfillerStatus?.name() ?: "" %>
        <tr>
            <td>${ ui.formatDatetimePretty(labOrder.effectiveStartDate) }</td>
            <td>${ ui.format(labOrder.orderNumber) }</td>
            <td>
                <% if (ui.format(labOrder.urgency) == 'STAT') { %>
                    <i class="fas fa-fw fa-exclamation" style="color: red;"></i>
                <% } %>
                ${ pihAppsUtils.formatLabTest(labOrder.concept) }</td>
            <td>${ ui.format(labOrder.orderer) }</td>
            <td>${ ui.message(status == "" ? "pihapps.ordered" : "pihapps.fulfillerStatus." + status) }</td>
            <td class="order-actions-btn" style="text-align: center;">
                <% if (labOrder.orderType == labOrderType && status != 'IN_PROGRESS' && status != 'COMPLETED') { %>
                    <span>
                        <a href="#" onclick="discontinueOrder('${labOrder.uuid}', '${labOrder.concept.uuid}')"><i class="icon-remove scale" title="${ui.message("pihapps.discontinue")}"></i></a>
                    </span>
                <% } %>
            </td>
        </tr>
    <% } %>
    </tbody>
</table>

<br/>

<h3>${ ui.message("pihapps.labOrders.inactive") }</h3>

<table id="inactive-orders-list" width="100%" border="1" cellspacing="0" cellpadding="2">
    <thead>
    <tr>
        <th>${ ui.message("pihapps.orderDate") }</th>
        <th>${ ui.message("pihapps.orderNumber") }</th>
        <th>${ ui.message("pihapps.labTest") }</th>
        <th>${ ui.message("pihapps.testOrderedBy") }</th>
        <th>${ ui.message("pihapps.status")} </th>
    </tr>
    </thead>
    <tbody>
    <% if (inactiveOrders.isEmpty()) { %>
    <tr>
        <td colspan="6">${ ui.message("coreapps.none") }</td>
    </tr>
    <% } %>
    <% inactiveOrders.each { labOrder ->
        def status = labOrder.fulfillerStatus?.name() ?: "" %>
    <tr>
        <td>${ ui.formatDatePretty(labOrder.effectiveStartDate) }</td>
        <td>${ ui.format(labOrder.orderNumber) }</td>
        <td>${ pihAppsUtils.formatLabTest(labOrder.concept) }</td>
        <td>${ ui.format(labOrder.orderer) }</td>
        <td>${ ui.message("pihapps." + (status == "" ? labOrder.isDiscontinuedRightNow() ? "discontinued" : "expired" : "fulfillerStatus." + status)) }</td>
    </tr>
    <% } %>
    </tbody>
</table>

<div id="discontinue-order-dialog" class="dialog" style="display: none;">
    <div class="dialog-header">
        <i class="icon-remove"></i>
        <h3>${ui.message("pihapps.discontinueOrder")}</h3>
    </div>
    <div class="dialog-content form">
        ${ui.message("pihapps.discontinueReason")}
        <br>
        <textarea id="discontinue-reason-field" type="text" rows="3" cols="40"></textarea>
        <br><br>
        <button class="cancel">${ ui.message("coreapps.cancel") }</button>
        <button class="confirm right">${ ui.message("coreapps.confirm") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
    </div>
</div>