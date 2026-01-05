<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "conceptUtils.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")

    def now = new Date()
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">

    const patientUuid = '${patient.patient.uuid}';

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

    moment.locale(window.sessionContext?.locale ?? 'en');

    const pagingDataTable = new PagingDataTable(jq);

    jq(document).ready(function() {

        const conceptRep = "(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))";
        const labOrderConfigRep = "(availableLabTestsByCategory:(category:" + conceptRep + ",labTests:" + conceptRep + "),orderStatusOptions:(status,display),fulfillerStatusOptions:(status,display),orderFulfillmentStatusOptions:(status,display))";
        const rep = "dateFormat,dateTimeFormat,primaryIdentifierType:(uuid),labOrderConfig:" + labOrderConfigRep;

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + rep + ")", function(pihAppsConfig) {

            const dateFormat = pihAppsConfig.dateFormat ?? "DD-MMM-YYYY";
            const dateTimeFormat = pihAppsConfig.dateTimeFormat ?? "DD-MMM-YYYY HH:mm";
            const conceptUtils = new PihAppsConceptUtils(jq);
            const patientUtils = new PihAppsPatientUtils(jq);
            const dateUtils = new PihAppsDateUtils(moment);
            const orderStatusOptions = pihAppsConfig.labOrderConfig.orderStatusOptions;
            const fulfillerStatusOptions = pihAppsConfig.labOrderConfig.fulfillerStatusOptions;
            const orderFulfillmentStatusOptions = pihAppsConfig.labOrderConfig.orderFulfillmentStatusOptions;

            // Column functions
            const getOrderDate = (order) => { return dateUtils.formatDateWithTimeIfPresent(order.dateActivated, dateFormat, dateTimeFormat); };
            const getOrderNumber = (order) => { return order.orderNumber; }
            const getOrderer = (order) => { return order.orderer.person.display; }
            const getOrderStatus = (order) => { return patientUtils.getOrderStatusOption(order, orderStatusOptions).display; };
            const getFulfillerStatus = (order) => { return patientUtils.getFulfillerStatusOption(order, fulfillerStatusOptions).display; };
            const getOrderFulfillmentStatus = (order) => { return patientUtils.getOrderFulfillmentStatusOption(order, orderFulfillmentStatusOptions).display; };
            const getLabTest = function(order) {
                const urgency = order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '';
                return urgency + conceptUtils.getConceptShortName(order.concept, window.sessionContext?.locale);
            }
            const getActions = function (order) {
                const orderStatusOption = patientUtils.getOrderStatusOption(order, orderStatusOptions);
                if (orderStatusOption.status === 'ACTIVE') {
                    const discontinueLink = '<a href="#" onClick="discontinueOrder(\\\'' + order.uuid + '\\\', \\\'' + order.concept.uuid + '\\\', \\\'' + order.careSetting.uuid + '\\\')"><i class="icon-remove scale"></i></a>';
                    return '<span class="order-actions-btn" style="text-align: center;">' + discontinueLink + '</span>'
                }
                return "";
            }

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
                representation: "custom:(id,uuid,display,orderNumber,orderer:(person:(display)),dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,concept:(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))",
                parameters: { ...getFilterParameterValues() },
                columnTransformFunctions: [
                    getOrderDate, getOrderNumber, getLabTest, getOrderer, getOrderFulfillmentStatus, getActions
                ],
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
                const optGroup = jq("<optGroup>").attr("label", conceptUtils.getConceptShortName(labCategory.category, window.sessionContext?.locale));
                labCategory.labTests.forEach((labTest) => {
                    const labOpt = jq("<option>").attr("value", labTest.uuid).html(conceptUtils.getConceptShortName(labTest, window.sessionContext?.locale));
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
    #test-filter-form {
        padding-bottom: 20px;
        table-layout: fixed;
    }
    #test-filter-form input {
        min-width: unset;
    }
    .date .small {
        font-size: unset;
    }
    .col {
        white-space: nowrap;
    }
    .info-and-paging-row {
        padding-top: 5px;
    }
    .paging-navigation {
        padding-left: 10px;
        cursor: pointer;
    }
    .order-actions-btn {
        width: 50%;
    }
    .order-actions-btn a i.scale {
        text-decoration: none;
        color: black;
    }
    .order-actions-btn :hover {
        transform: scale(1.5);
    }
</style>

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

<table id="orders-table">
    <thead>
        <tr>
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