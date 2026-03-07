<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "conceptUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">

    const patientUuid = '${patient.patient.uuid}';
    const patientListPage = '${ui.pageLink("pihapps", "labs/labPatientList")}';

    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labPatientList")) }" , link: '${ui.pageLink("pihapps", "labs/labPatientList")}'},
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labPatientReception")) }" , link: '${ui.pageLink("pihapps", "labs/labPatientReception", ["patientId": patient.id])}'}
    ];

    moment.locale(window.sessionContext?.locale ?? 'en');

    const pagingDataTable = new PagingDataTable(jq);

    jq(document).ready(function() {

        const conceptRep = "(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))";
        const labOrderConfigRep = "(labTestOrderType:(uuid),availableLabTestsByCategory:(category:" + conceptRep + ",labTests:" + conceptRep + "),orderStatusOptions:(status,display),fulfillerStatusOptions:(status,display),orderFulfillmentStatusOptions:(status,display),testLocationQuestion:(uuid,answers:(uuid,display)),specimenCollectionEncounterType:(uuid),specimenCollectionEncounterRole:(uuid),estimatedCollectionDateQuestion:(uuid),estimatedCollectionDateAnswer:(uuid),testOrderNumberQuestion:(uuid),labIdentifierConcept:(uuid),specimenReceivedDateQuestion:(uuid),reasonTestNotPerformedQuestion:(uuid,answers:(uuid,display)))";
        const rep = "dateFormat,dateTimeFormat,primaryIdentifierType:(uuid),labOrderConfig:" + labOrderConfigRep;

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + rep + ")", function(pihAppsConfig) {

            const conceptUtils = new PihAppsConceptUtils(jq);
            const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);

            // Column functions
            const getOrderDate = (order) => { return dateUtils.formatDateWithTimeIfPresent(order.dateActivated); };
            const getOrderNumber = (order) => { return order.orderNumber; }
            const getOrderer = (order) => { return order.orderer.person.display; }
            const getLabTest = function(order) {
                const urgency = order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '';
                return urgency + conceptUtils.getConceptShortName(order.concept, window.sessionContext?.locale);
            }
            const getSelectCheckbox = (order) => {
                return '<input class="order-selector" type="checkbox" value="' + order.uuid + '" />';
            }

            const getFilterParameterValues = function() {
                return {
                    "patient": patientUuid,
                    "orderType": pihAppsConfig.labOrderConfig.labTestOrderType?.uuid,
                    "orderFulfillmentStatus": "AWAITING_FULFILLMENT",
                    "sortBy": "dateActivated-desc"
                }
            }

            const getSelectedOrders = function() {
                const orderUuids = [];
                jq("#orders-table").find(".order-selector:checked").each(function(index, element) {
                    orderUuids.push(jq(element).val())
                });
                return pagingDataTable.getRowObjects().filter((o) => orderUuids.includes(o.uuid));
            }

            jq("#select-all-orders").change(function () {
                if (jq(this).prop("checked")) {
                    jq(".order-selector").prop("checked", "checked");
                }
                else {
                    jq(".order-selector").removeAttr("checked");
                }
            });

            jq("#back-button").click(() => { document.location.href = patientListPage; })

            jq("#process-orders-button").click(function() {
                jq("#view-orders-section").hide();
                const selectedOrders = getSelectedOrders();
                initializeSpecimenCollectionForm({patientUuid: patientUuid, orders: selectedOrders, pihAppsConfig: pihAppsConfig});
            });

            jq("#remove-orders-button").click(function() {
                const ordersWidgetsSection = jq("#remove-orders-form .orders-widgets");
                ordersWidgetsSection.html("");
                const selectedOrders = getSelectedOrders();
                if (selectedOrders.length > 0) {
                    populateOrderWidgetsSection(ordersWidgetsSection, selectedOrders);
                    jq("#remove-orders-errors-section").html("");
                    jq("#remove-orders-form").find(":input").val("");

                    const reasonTestNotPerformedQuestion = pihAppsConfig.labOrderConfig.reasonTestNotPerformedQuestion;
                    if (!reasonTestNotPerformedQuestion || reasonTestNotPerformedQuestion.answers.length === 0) {
                        jq("#remove-reason-section").hide();
                    }

                    jq("#view-orders-section").hide();
                    jq("#remove-orders-section").show();
                }
            });

            const getFieldValue = function(formData, fieldName) {
                return formData.find(e => e.name === fieldName)?.value ?? '';
            }

            const disableFormEntry = () => { jq(".action-button").attr("disabled", "disabled") };
            const enableFormEntry = () => { jq(".action-button").removeAttr("disabled") };

            jq("#remove-orders-form").submit((event) => {
                event.preventDefault();
                disableFormEntry();
                const selectedOrders = getSelectedOrders();
                const formData = jq("#remove-orders-form").serializeArray();
                jq("#remove-orders-errors-section").html("");
                const removeReason = getFieldValue(formData, "remove-reason-dropdown");
                if (!removeReason) {
                    jq("#remove-orders-errors-section").append(jq("<div>").html('${ ui.message("pihapps.reasonRequired") }'));
                    enableFormEntry();
                    return;
                }

                const removeOrdersPayload = {
                    "orders": selectedOrders.map(o => o.uuid),
                    "reason": removeReason
                }
                jq.ajax({
                    url: openmrsContextPath + "/ws/rest/v1/pihapps/markOrdersAsNotPerformed",
                    type: "POST",
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify(removeOrdersPayload),
                    dataType: "json",
                    success: () => {
                        document.location.href = patientListPage;
                    },
                    error: (xhr) => {
                        enableFormEntry();
                        const error = xhr?.responseJSON?.error;
                        const message = error?.translatedMessage ?? error.message ?? error;
                        jq("#remove-orders-errors-section").html(message);
                    }
                });
            });

            jq("#process-orders-section button.cancel").click((event) => {
                event.preventDefault();
                jq("#process-orders-section").hide();
                jq("#view-orders-section").show();
            });

            jq("#remove-orders-section button.cancel").click((event) => {
                event.preventDefault();
                jq("#remove-orders-section").hide();
                jq("#view-orders-section").show();
            });

            pihAppsConfig.labOrderConfig.reasonTestNotPerformedQuestion?.answers?.forEach((answer) => {
                jq("#remove-reason-picker-field").append(jq("<option>").attr("value", answer.uuid).html(answer.display));
            });

            pagingDataTable.initialize({
                tableSelector: "#orders-table",
                tableInfoSelector: "#orders-table-info-and-paging",
                endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/labOrder",
                representation: "custom:(id,uuid,display,orderNumber,orderer:(person:(display)),dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,concept:(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))",
                parameters: { ...getFilterParameterValues() },
                columnTransformFunctions: [
                    getSelectCheckbox, getOrderDate, getOrderNumber, getLabTest, getOrderer
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
        });
    });
</script>

<style>
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
    .dataTables_wrapper {
        min-height: unset;
    }
    #order-actions-section {
        padding-top: 20px;
    }

    .form-field-label {
        line-height: 3;
    }
    .form-field-widgets {
        padding-left: 20px;
    }
    .form-field-widgets label {
        display: none;
    }
    .form-field-widgets p {
        display: inline;
    }
    .dialog {
        width: 80%;
        height: 90%;
    }
    .dialog select option {
        font-size: 1.0em;
    }
    form input, form select, form textarea, .form input, .form select, .form textarea {
        min-width: auto;
        width: fit-content;
    }
    .table-header div {
        font-weight: bold;
    }
    .form-header {
        padding-top: 10px;
        font-weight: bold;
        font-size: large;
    }
    .errors-section {
        font-weight: bold;
        color: red;
    }
    #process-orders-section {
        display:none;
    }
</style>

<div class="row justify-content-between" style="padding-top: 10px">
    <div class="col-6">
        <h3>${ ui.message("pihapps.labPatientReception") }</h3>
    </div>
</div>

<div id="view-orders-section">
    <table id="orders-table">
        <thead>
            <tr>
                <th><input id="select-all-orders" type="checkbox" /> ${ ui.message("pihapps.all") }</th>
                <th>${ ui.message("pihapps.orderDate") }</th>
                <th>${ ui.message("pihapps.orderNumber") }</th>
                <th>${ ui.message("pihapps.labTest") }</th>
                <th>${ ui.message("pihapps.testOrderedBy") }</th>
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
    <div id="order-actions-section">
        <input type="button" id="back-button" class="cancel" value="${ ui.message("pihapps.return") }" />
        <input type="button" id="process-orders-button" value="${ ui.message("pihapps.processSelectedOrders") }" />
        <input type="button" id="remove-orders-button" value="${ ui.message("pihapps.removeSelectedOrders") }" />
    </div>
</div>

${ ui.includeFragment("pihapps", "labs/specimenCollectionEncounter", ["id": "process-orders-section"])}

<div id="remove-orders-section" style="display:none;">
    <div class="form-header">
        ${ui.message("pihapps.removeSelectedOrders")}
    </div>
    <div id="remove-orders-errors-section" class="errors-section"></div>
    <form id="remove-orders-form">
        <div class="dialog-content form">
            <div class="orders-section" class="form-field-section row">
                <span class="orders-label" class="form-field-label col-4">${ui.message("pihapps.selectedOrders")}:</span>
                <span class="orders-widgets" class="form-field-widgets col-8">

                </span>
            </div>
            <div id="remove-reason-section" class="form-field-section row">
                <span id="remove-reason-label" class="form-field-label col-4">${ui.message("pihapps.reason")}:</span>
                <span id="remove-reason-widgets" class="form-field-widgets col-auto">
                    ${ui.includeFragment("uicommons", "field/dropDown", [
                            id: "remove-reason-picker",
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