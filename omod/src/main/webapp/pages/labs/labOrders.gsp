<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "specimenCollection.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def now = new Date()
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">

    const patientUuid = '${patient.patient.uuid}';
    const sessionLocationIsAdmission = ${ sessionLocationIsAdmission ? 'true' : 'false' };

    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.escapeJs(ui.format(patient.patient)) }" , link: '${ui.urlBind("/" + contextPath + pihAppsConfig.getDashboardUrl(), ["patientId": patient.id])}'},
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labOrders")) }" , link: '${ui.pageLink("pihapps", "labs/labOrders", ["patientId": patient.id])}'}
    ];

    function discontinueOrder(orderUuid, orderableUuid, careSettingUuid) {
        const discontinueDialog = emr.setupConfirmationDialog({
            selector: '#discontinue-order-dialog',
            actions: {
                confirm: function() {

                    const discontinueReason = jq("#discontinue-reason-field").val();
                    const discontinueDate = moment().format('YYYY-MM-DDTHH:mm:ss.SSS');
                    const orderer = '${sessionContext.currentProvider.uuid}';
                    const rep = 'custom:(labOrderConfig:(labOrderEncounterType:(uuid),labOrderEncounterRole:(uuid),labTestOrderType:(uuid),defaultCareSetting:(uuid)))'
                    jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=" + rep, function(pihAppsConfig) {
                        const labOrderConfig = pihAppsConfig.labOrderConfig;
                        const encounterPayload = {
                            patient: patientUuid,
                            encounterType: labOrderConfig.labOrderEncounterType,
                            encounterDatetime: discontinueDate,
                            location: '${sessionContext.sessionLocation.uuid}',
                            encounterProviders: [ { encounterRole: labOrderConfig.labOrderEncounterRole, provider: orderer } ],
                            orders: [
                                {
                                    type: 'testorder',
                                    action: 'DISCONTINUE',
                                    previousOrder: orderUuid,
                                    patient: patientUuid,
                                    orderer: orderer,
                                    orderType: labOrderConfig.labTestOrderType?.uuid,
                                    concept: orderableUuid,
                                    urgency: 'ROUTINE',
                                    orderReasonNonCoded: discontinueReason,
                                    careSetting: careSettingUuid,
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

    const openSection = function(selector) {
        jq("#view-orders-section").hide();
        jq(selector).show();
    }

    const closeSection = function(selector) {
        jq(selector).hide();
        jq("#view-orders-section").show();
    }

    const openEncounterEdit = () => openSection("#edit-specimen-encounter-section");
    const closeEncounterEdit = () => closeSection("#edit-specimen-encounter-section");

    moment.locale(window.sessionContext?.locale ?? 'en');

    const pagingDataTable = new PagingDataTable(jq);

    jq(document).ready(function() {

        const conceptRep = "(id,uuid,allowDecimal,display,displayStringForLab)";
        const labOrderConfigRep = "(availableLabTestsByCategory:(category:" + conceptRep + ",labTests:" + conceptRep + "),orderStatusOptions:(status,display),fulfillerStatusOptions:(status,display),orderFulfillmentStatusOptions:(status,display)," + PihAppsSpecimenCollection.getConfigRep() + ")";
        const rep = "dateFormat,dateTimeFormat,primaryIdentifierType:(uuid),labOrderConfig:" + labOrderConfigRep;

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + rep + ")", function(pihAppsConfig) {

            const patientUtils = new PihAppsPatientUtils(jq);
            const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat)
            const orderStatusOptions = pihAppsConfig.labOrderConfig.orderStatusOptions;
            const fulfillerStatusOptions = pihAppsConfig.labOrderConfig.fulfillerStatusOptions;
            const orderFulfillmentStatusOptions = pihAppsConfig.labOrderConfig.orderFulfillmentStatusOptions;

            // Column functions
            const getOrderDate = (order) => { return dateUtils.formatDateWithTimeIfPresent(order.dateActivated); };
            const getOrderNumber = (order) => { return order.orderNumber; }
            const getOrderer = (order) => { return order.orderer.person.display; }
            const getOrderStatus = (order) => { return patientUtils.getOrderStatusOption(order, orderStatusOptions).display; };
            const getFulfillerStatus = (order) => { return patientUtils.getFulfillerStatusOption(order, fulfillerStatusOptions).display; };
            const getOrderFulfillmentStatus = (order) => { return patientUtils.getOrderFulfillmentStatusOption(order, orderFulfillmentStatusOptions).display; };
            const getLabTest = function(order) {
                return (order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '') + order.concept.displayStringForLab;
            }
            // Looks up an order on the page currently shown in the table, so that action handlers can find it
            const getDisplayedOrder = function(uuid) {
                return pagingDataTable.getRowObjects().find(o => o.uuid === uuid);
            }

            const specimenCollection = new PihAppsSpecimenCollection({
                openForm: openEncounterEdit,
                closeForm: closeEncounterEdit,
                loadErrorMessage: "${ ui.encodeJavaScript(ui.message("pihapps.specimenCollectionLoadErrorMessage")) }"
            });

            // Orders selected for a single specimen collection, keyed by order uuid so that the selection survives paging
            const selectedOrders = new Map();

            const updateSelectionBar = function() {
                jq("#selected-orders-count").text(selectedOrders.size);
                jq("#selected-orders-bar").toggle(selectedOrders.size > 0);
            }

            const clearSelection = function() {
                selectedOrders.clear();
                jq("#orders-table .select-order-checkbox").prop("checked", false);
                updateSelectionBar();
            }

            const refreshOrders = function() {
                clearSelection();
                pagingDataTable.updateTable();
            }

            // Only orders that can still be collected get a checkbox
            const getSelectOrderCheckbox = function(order) {
                if (!sessionLocationIsAdmission || !PihAppsSpecimenCollection.isCollectable(order)) {
                    return "";
                }
                return '<input type="checkbox" class="select-order-checkbox" data-order-uuid="' + order.uuid + '"' + (selectedOrders.has(order.uuid) ? ' checked' : '') + '/>';
            }

            // At admission locations, orders that are awaiting collection can have their specimen collected,
            // and orders that have already been collected can have their specimen collection edited
            const getCollectSpecimenAction = function(order) {
                if (!sessionLocationIsAdmission) {
                    return "";
                }
                let title = null;
                if (order.fulfillerEncounter) {
                    title = "${ ui.encodeJavaScript(ui.encodeHtmlAttribute(ui.message("pihapps.editSpecimenCollection"))) }";
                } else if (PihAppsSpecimenCollection.isCollectable(order)) {
                    title = "${ ui.encodeJavaScript(ui.encodeHtmlAttribute(ui.message("pihapps.collectSpecimen"))) }";
                }
                if (!title) {
                    return "";
                }
                return '<span class="order-specimen-actions"><i class="fas fa-fw fa-vial collect-specimen-action" data-order-uuid="' + order.uuid + '" title="' + title + '"></i></span>';
            }

            const getActions = function (order) {
                const orderStatusOption = patientUtils.getOrderStatusOption(order, orderStatusOptions);
                let actions = getCollectSpecimenAction(order);
                if (orderStatusOption.status === 'ACTIVE') {
                    const discontinueLink = '<a href="#" onClick="discontinueOrder(\\\'' + order.uuid + '\\\', \\\'' + order.concept.uuid + '\\\', \\\'' + order.careSetting.uuid + '\\\')"><i class="icon-remove scale"></i></a>';
                    actions += '<span class="order-actions-btn" style="text-align: center;">' + discontinueLink + '</span>';
                }
                return actions;
            }

            const editSpecimenCollection = function(order) {
                specimenCollection.edit({
                    encounterUuid: order.fulfillerEncounter.uuid,
                    pihAppsConfig: pihAppsConfig,
                    onSuccess: refreshOrders
                });
            }

            const collectSpecimen = function(order) {
                specimenCollection.collect({
                    patientUuid: patientUuid,
                    orders: [order],
                    selectedOrderUuids: [order.uuid],
                    pihAppsConfig: pihAppsConfig,
                    onSuccess: refreshOrders
                });
            }

            jq("#orders-table").on("click", ".collect-specimen-action", function(event) {
                event.stopPropagation();
                const order = getDisplayedOrder(jq(event.currentTarget).attr("data-order-uuid"));
                if (order && order.fulfillerEncounter) {
                    editSpecimenCollection(order);
                } else if (order) {
                    collectSpecimen(order);
                }
            });

            jq("#orders-table").on("change", ".select-order-checkbox", function(event) {
                const uuid = jq(event.currentTarget).attr("data-order-uuid");
                const order = getDisplayedOrder(uuid);
                if (jq(event.currentTarget).is(":checked") && order) {
                    selectedOrders.set(uuid, order);
                } else {
                    selectedOrders.delete(uuid);
                }
                updateSelectionBar();
            });

            jq("#clear-selected-orders").click(function(event) {
                event.preventDefault();
                clearSelection();
            });

            jq("#collect-selected-orders").click(function(event) {
                event.preventDefault();
                const orders = Array.from(selectedOrders.values());
                if (orders.length > 0) {
                    specimenCollection.collect({
                        patientUuid: patientUuid,
                        orders: orders,
                        selectedOrderUuids: orders.map(o => o.uuid),
                        pihAppsConfig: pihAppsConfig,
                        onSuccess: refreshOrders
                    });
                }
            });

            jq("#specimen-encounter-section button.cancel").click(function(event) {
                event.preventDefault();
                closeEncounterEdit();
            });

            const getFilterParameterValues = function() {
                return {
                    "patient": patientUuid,
                    "labTest": jq("#testConcept-filter").val(),
                    "activatedOnOrAfter": jq("#orderedFrom-filter-field").val(),
                    "activatedOnOrBefore": jq("#orderedTo-filter-field").val(),
                    "accessionNumber": jq("#lab-id-filter").val(),
                    "orderFulfillmentStatus": jq("#orderFulfillmentStatus-filter").val(),
                    "sortBy": "dateActivated-desc"  // TODO: Sorting by dateActivated desc does not seem right, but doing this to match existing labWorkflow, but shouldn't this order by urgency and asc?
                }
            }

            pagingDataTable.initialize({
                tableSelector: "#orders-table",
                tableInfoSelector: "#orders-table-info-and-paging",
                endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/labOrder",
                representation: "custom:(id,uuid,display,orderNumber,orderer:(person:(display)),dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,fulfillerEncounter:(id,uuid,display,encounterDatetime),orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,concept:(id,uuid,allowDecimal,display,displayStringForLab)",
                parameters: { ...getFilterParameterValues() },
                columnTransformFunctions: (sessionLocationIsAdmission ? [getSelectOrderCheckbox] : []).concat([
                    getOrderDate, getOrderNumber, getLabTest, getOrderer, getOrderFulfillmentStatus, getActions
                ]),
                datatableOptions: {
                    oLanguage: {
                        sInfo: "${ ui.message("uicommons.dataTable.info") }",
                        sZeroRecords: "${ ui.message("uicommons.dataTable.zeroRecords") }",
                        sEmptyTable: "${ ui.message("uicommons.dataTable.emptyTable") }",
                        sInfoEmpty:  "${ ui.message("uicommons.dataTable.infoEmpty") }",
                        sLoadingRecords:  "${ ui.message("uicommons.dataTable.loadingRecords") }",
                        sProcessing:  "${ ui.message("uicommons.dataTable.processing") }",
                    }
                }
            });

            pihAppsConfig.labOrderConfig.availableLabTestsByCategory.forEach((labCategory) => {
                const optGroup = jq("<optGroup>").attr("label", labCategory.category.displayStringForLab);
                labCategory.labTests.forEach((labTest) => {
                    const labOpt = jq("<option>").attr("value", labTest.uuid).html(labTest.displayStringForLab);
                    optGroup.append(labOpt);
                });
                jq("#testConcept-filter").append(optGroup);
            });

            orderFulfillmentStatusOptions.forEach((statusOption) => {
                const option = jq("<option>").attr("value", statusOption.status).html(statusOption.display);
                jq("#orderFulfillmentStatus-filter").append(option);
            });

            jq("#test-filter-form").find(":input").change(function () {
                pagingDataTable.setParameters(getFilterParameterValues())
                pagingDataTable.goToFirstPage();
            });
        });
    });
</script>
<style>
    #edit-specimen-encounter-section { display: none; }
    #selected-orders-bar {
        background-color: #d0dcdc;
        border: 1px solid #b3c5c5;
        margin: 0 0 5px 0;
        padding: 5px 10px;
    }
    #selected-orders-bar #clear-selected-orders {
        margin-left: 10px;
    }
    #selected-orders-bar button {
        margin: 0;
    }
    .order-specimen-actions .collect-specimen-action {
        cursor: pointer;
        padding: 0 4px;
        color: #495057;
    }
    .order-specimen-actions .collect-specimen-action:hover {
        color: #0056b3;
    }
</style>
<div id="view-orders-section">
<div class="row justify-content-between" style="padding-top: 10px">
    <div class="col-6">
        <h3>${ ui.message("pihapps.labOrders") }</h3>
    </div>
    <div class="col-6 text-right">
        <a href="${ui.pageLink("pihapps", "labs/labOrder", ["patient": patient.patient.uuid])}">
            <input type="button" value="${ui.message("pihapps.addLabOrders")}" style="max-width: unset;"/>
        </a>
    </div>
</div>
<form method="get" id="test-filter-form">
    <div class="row justify-content-start align-items-end">
        <div class="col">
            ${ ui.includeFragment("pihapps", "field/datetimepicker", [
                    id: "orderedFrom-filter",
                    formFieldName: "orderedFrom",
                    label: "pihapps.orderedFrom",
                    classes: "form-control",
                    endDate: now,
                    useTime: false,
                    clearButton: true
            ])}
        </div>
        <div class="col">
            ${ ui.includeFragment("pihapps", "field/datetimepicker", [
                    id: "orderedTo-filter",
                    formFieldName: "orderedTo",
                    label: "pihapps.orderedTo",
                    classes: "form-control",
                    endDate: now,
                    useTime: false,
                    clearButton: true
            ])}
        </div>
        <div class="col">
            <label for="orderFulfillmentStatus-filter">${ ui.message("pihapps.orderStatus") }</label>
            <select id="orderFulfillmentStatus-filter" name="orderFulfillmentStatus" class="form-control"></select>
        </div>
        <div class="col">
            <label for="testConcept-filter">${ ui.message("pihapps.labTest") }</label>
            <select id="testConcept-filter" name="testConcept" class="form-control">
                <option value=""></option>
            </select>
        </div>
    </div>
</form>

<% if (sessionLocationIsAdmission) { %>
<div id="selected-orders-bar" class="row justify-content-between align-items-center" style="display: none;">
    <div class="col">
        <span><strong id="selected-orders-count">0</strong> ${ ui.message("pihapps.testsSelected") }</span>
        <a href="#" id="clear-selected-orders">${ ui.message("pihapps.clearSelection") }</a>
    </div>
    <div class="col text-right">
        <button type="button" id="collect-selected-orders"><i class="fas fa-fw fa-vial"></i> ${ ui.message("pihapps.specimenCollection") }</button>
    </div>
</div>
<% } %>

<table id="orders-table">
    <thead>
        <tr>
            <% if (sessionLocationIsAdmission) { %><th></th><% } %>
            <th>${ ui.message("pihapps.orderDate") }</th>
            <th>${ ui.message("pihapps.orderNumber") }</th>
            <th>${ ui.message("pihapps.labTest") }</th>
            <th>${ ui.message("pihapps.testOrderedBy") }</th>
            <th>${ ui.message("pihapps.orderFulfillmentStatus") }</th>
            <th>${ ui.message("pihapps.actions") }</th>
        </tr>
    </thead>
    <tbody></tbody>
</table>
<div id="orders-table-info-and-paging" style="font-size: .9em">
    <div class="row justify-content-between info-and-paging-row">
        <div class="col paging-info"></div>
        <div class="col text-right">
            <a class="first paging-navigation">${ ui.message("uicommons.dataTable.first") }</a>
            <a class="previous paging-navigation">${ ui.message("uicommons.dataTable.previous") }</a>
            <a class="next paging-navigation">${ ui.message("uicommons.dataTable.next") }</a>
            <a class="last paging-navigation">${ ui.message("uicommons.dataTable.last") }</a>
        </div>
    </div>
    <div class="row justify-content-between info-and-paging-row">
        <div class="col paging-size">${ ui.message("uicommons.dataTable.lengthMenu") }</div>
    </div>
</div>
</div>

<div id="edit-specimen-encounter-section">
    <div class="row justify-content-between">
        <div class="col-6">
            <h3>${ ui.message("pihapps.specimenCollectionDetails") } - ${ ui.encodeHtmlContent(ui.format(patient.patient)) }</h3>
        </div>
    </div>
    ${ ui.includeFragment("pihapps", "labs/specimenCollectionEncounter", ["id": "specimen-encounter-section"])}
</div>

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