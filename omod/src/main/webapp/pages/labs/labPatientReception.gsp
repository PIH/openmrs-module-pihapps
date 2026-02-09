<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "conceptUtils.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")

    def now = new Date()
    def specimenCollectionEncounterRole = pihAppsConfig.labOrderConfig.specimenCollectionEncounterRole
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">

    const patientUuid = '${patient.patient.uuid}';
    const defaultOrderer = '${sessionContext.currentProvider.uuid}';
    const defaultLocation = '${sessionContext.sessionLocation.uuid}'
    const patientListPage = '${ui.pageLink("pihapps", "labs/labPatientList")}';

    const messageCodes = {
        specimenCollectionDateCannotBeFuture: '${ ui.message("pihapps.specimenCollectionDateCannotBeFuture") }',
        specimenReceivedDateCannotBeFuture: '${ ui.message("pihapps.specimenReceivedDateCannotBeFuture") }',
        specimenReceivedCannotBeBeforeCollected: '${ ui.message("pihapps.specimenReceivedCannotBeBeforeCollected") }'
    };

    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labPatientList")) }" , link: '${ui.pageLink("pihapps", "labs/labPatientList")}'},
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labPatientReception")) }" , link: '${ui.pageLink("pihapps", "labs/labPatientReception", ["patientId": patient.id])}'}
    ];

    moment.locale(window.sessionContext?.locale ?? 'en');

    const pagingDataTable = new PagingDataTable(jq);

    jq(document).ready(function() {

        const conceptRep = "(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))";
        const labOrderConfigRep = "(labTestOrderType:(uuid),availableLabTestsByCategory:(category:" + conceptRep + ",labTests:" + conceptRep + "),orderStatusOptions:(status,display),fulfillerStatusOptions:(status,display),orderFulfillmentStatusOptions:(status,display),testLocationQuestion:(uuid,answers:(uuid,display)),specimenCollectionEncounterType:(uuid),specimenCollectionEncounterRole:(uuid),estimatedCollectionDateQuestion:(uuid),estimatedCollectionDateAnswer:(uuid),testOrderNumberQuestion:(uuid),labIdentifierConcept:(uuid),specimenReceivedDateQuestion:(uuid))";
        const rep = "dateFormat,dateTimeFormat,primaryIdentifierType:(uuid),labOrderConfig:" + labOrderConfigRep;

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + rep + ")", function(pihAppsConfig) {

            const dateFormat = pihAppsConfig.dateFormat ?? "DD-MMM-YYYY";
            const dateTimeFormat = pihAppsConfig.dateTimeFormat ?? "DD-MMM-YYYY HH:mm";
            const conceptUtils = new PihAppsConceptUtils(jq);
            const patientUtils = new PihAppsPatientUtils(jq);
            const dateUtils = new PihAppsDateUtils(moment);

            // Column functions
            const getOrderDate = (order) => { return dateUtils.formatDateWithTimeIfPresent(order.dateActivated, dateFormat, dateTimeFormat); };
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

            const getSelectedOrderData = function() {
                const ret = [];
                jq("#orders-table").find(".order-selector").each(function(index, element) {
                    if (jq(element).prop("checked")) {
                        const columns = jq(element).closest("tr").find("td");
                        ret.push({
                            "uuid": jq(element).val(),
                            "orderDate": columns.eq(1).html(),
                            "orderNumber": columns.eq(2).html(),
                            "labTest": columns.eq(3).html(),
                            "orderedBy": columns.eq(4).html(),
                        });
                    }
                });
                return ret;
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
                const ordersWidgetsSection = jq("#orders-widgets");
                ordersWidgetsSection.html("");
                const selectedOrderData = getSelectedOrderData();
                if (selectedOrderData.length > 0) {
                    let headerRow = jq("<div>").addClass("row table-header");
                    headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.labTest") }"));
                    headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.orderDate") }"));
                    headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.orderNumber") }"));
                    ordersWidgetsSection.append(headerRow);
                    selectedOrderData.forEach((orderRow) => {
                        let row = jq("<div>").addClass("row");
                        row.append(jq("<div>").addClass("col-4").html(orderRow.labTest));
                        row.append(jq("<div>").addClass("col-4").html(orderRow.orderDate));
                        row.append(jq("<div>").addClass("col-4").html(orderRow.orderNumber));
                        ordersWidgetsSection.append(row);
                    });

                    // Populate default values each time form is opened
                    jq("#errors-section").html("");
                    jq("#process-orders-form").find(":input").val("");
                    const currentDatetime = dateUtils.roundDownToNearestMinuteInterval(new Date(), 5);
                    jq("#specimen-date-estimated").attr("value", pihAppsConfig.labOrderConfig.estimatedCollectionDateAnswer.uuid).removeAttr("checked");
                    jq("#specimen-date-picker-wrapper").datetimepicker("option", "maxDateTime", currentDatetime);
                    jq("#specimen-date-picker-wrapper").datetimepicker("setDate", currentDatetime);
                    jq("#specimen-location-picker-field").val(defaultLocation);

                    // Open the form
                    jq("#view-orders-section").hide();
                    jq("#process-orders-section").show();
                }
            });

            const getFieldValue = function(formData, fieldName) {
                return formData.find(e => e.name === fieldName)?.value ?? '';
            }

            const getObs = function(formData, fieldName, concept) {
                return {
                    concept: concept,
                    value: getFieldValue(formData, fieldName),
                    formNamespaceAndPath: 'pihapps^' + fieldName
                }
                // Note: In the labworkflow owa version, order was set on obs, but we do not do this here as there could be multiple orders
                // Note: In the labworkflow owa, formNamespaceAndPath was stored in the comment.  Here we move this, and rename them as well
            }

            const validateFormData = function(formData) {
                const errors = [];
                const currentDate = moment();
                const collectionDateStr =  getFieldValue(formData, "specimen_collection_date");
                const collectionDate = collectionDateStr ? moment(collectionDateStr) : null;
                console.log('collection date field: ' + collectionDateStr + "; collection date moment: " + collectionDate.toDate());
                if (collectionDate && collectionDate.isAfter(currentDate)) {
                    errors.push(messageCodes.specimenCollectionDateCannotBeFuture);
                }
                const receivedDateStr = getFieldValue(formData, "specimen_received_date");
                const receivedDate = receivedDateStr ? moment(receivedDateStr) : null;
                if (receivedDate && receivedDate.isAfter(currentDate)) {
                    errors.push(messageCodes.specimenReceivedDateCannotBeFuture);
                }
                if (collectionDate && receivedDate && collectionDate.isAfter(receivedDate)) {
                    errors.push(messageCodes.specimenReceivedCannotBeBeforeCollected);
                }
                return errors;
            }

            const disableFormEntry = () => { jq("#process-orders-form .action-button").attr("disabled", "disabled") };
            const enableFormEntry = () => { jq("#process-orders-form .action-button").removeAttr("disabled") };

            jq("#process-orders-form").submit((event) => {
                event.preventDefault();
                disableFormEntry();
                const selectedOrders = getSelectedOrderData();
                const formData = jq("#process-orders-form").serializeArray();

                jq("#errors-section").html("");
                const validationErrors = validateFormData(formData);
                if (validationErrors && validationErrors.length > 0) {
                    validationErrors.forEach(e => {
                        jq("#errors-section").append(jq("<div>").html(e));
                    });
                    enableFormEntry();
                    return;
                }

                const encounterRole = pihAppsConfig.labOrderConfig.specimenCollectionEncounterRole?.uuid;
                const provider = formData.find(e => e.name === "specimen_collection_provider")?.value;
                const encounterProviders = (provider && encounterRole) ? [{ provider, encounterRole }] : [];

                const orderNumberObs = selectedOrders.map((o, index) => {
                    return {
                        concept: pihAppsConfig.labOrderConfig.testOrderNumberQuestion.uuid,
                        value: o.orderNumber,
                        formNamespaceAndPath: "pihapps^order_number_" + index
                    }
                });

                const encounterFulfillingOrders = {
                        encounter: {
                            patient: patientUuid,
                            encounterDatetime: formData.find(e => e.name === "specimen_collection_date").value,
                            encounterType: pihAppsConfig.labOrderConfig.specimenCollectionEncounterType?.uuid,
                            location: formData.find(e => e.name === "specimen_collection_location").value,
                            encounterProviders: encounterProviders,
                            obs: [
                                ...orderNumberObs,
                                getObs(formData, "lab_id", pihAppsConfig.labOrderConfig.labIdentifierConcept.uuid),
                                getObs(formData, "specimen_date_estimated", pihAppsConfig.labOrderConfig.estimatedCollectionDateQuestion.uuid),
                                getObs(formData, "specimen_received_date", pihAppsConfig.labOrderConfig.specimenReceivedDateQuestion.uuid),
                                getObs(formData, "test_location", pihAppsConfig.labOrderConfig.testLocationQuestion.uuid)
                            ].filter(o => o.value)
                        },
                        orders: selectedOrders.map(o => o.uuid)
                }
                jq.ajax({
                    url: openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders",
                    type: "POST",
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify(encounterFulfillingOrders),
                    dataType: "json",
                    success: () => {
                        document.location.href = patientListPage;
                    },
                    error: (xhr) => {
                        enableFormEntry();
                        const error = xhr?.responseJSON?.error;
                        const message = error?.translatedMessage ?? error.message ?? error;
                        jq("#errors-section").html(message);
                    }
                });
            });

            jq("#process-orders-section button.cancel").click((event) => {
                event.preventDefault();
                jq("#process-orders-section").hide();
                jq("#view-orders-section").show();
            });

            pihAppsConfig.labOrderConfig.testLocationQuestion?.answers?.forEach((answer) => {
                jq("#test-location-picker-field").append(jq("<option>").attr("value", answer.uuid).html(answer.display));
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
    #orders-section {
        padding: 10px 0 10px 0;
        background-color: lightgray;
        margin-bottom: 10px;
        padding-left: 5px;
    }
    .form-header {
        padding-top: 10px;
        font-weight: bold;
        font-size: large;
    }
    #errors-section {
        font-weight: bold;
        color: red;
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
        <input type="button" id="remove-orders-button" value="${ ui.message("pihapps.removeSelectedOrders") }" style="display:none;" />
    </div>
</div>

<div id="process-orders-section" style="display:none;">
    <div class="form-header">
        ${ui.message("pihapps.processSelectedOrders")}
    </div>
    <div id="errors-section"></div>
    <form id="process-orders-form">
        <div class="dialog-content form">
            <div id="orders-section" class="form-field-section row">
                <span id="orders-label" class="form-field-label col-4">${ui.message("pihapps.selectedOrders")}:</span>
                <span id="orders-widgets" class="form-field-widgets col-8">

                </span>
            </div>
            <div id="lab-id-section" class="form-field-section row">
                <span id="lab-id-label" class="form-field-label col-4">${ui.message("pihapps.labId")}:</span>
                <span id="lab-id-widgets" class="form-field-widgets col-auto">
                    ${ui.includeFragment("uicommons", "field/text", [
                            id: "lab-id-input",
                            label: "",
                            formFieldName: "lab_id",
                            left: true,
                            size: 20,
                            initialValue: ""
                    ])}
                </span>
            </div>
            <div id="specimen-date-section" class="form-field-section row align-items-start">
                <span id="specimen-date-label" class="form-field-label col-4">${ui.message("pihapps.specimenCollectionDate")}:</span>
                <span id="specimen-date-widgets" class="form-field-widgets col-auto">
                    ${ui.includeFragment("pihapps", "field/datetimepicker", [
                            id: "specimen-date-picker",
                            label: "",
                            formFieldName: "specimen_collection_date",
                            useTime: true,
                            left: true,
                            defaultDate: now
                    ])}
                </span>
                <span id="specimen-date-estimated-widgets" class="form-field-widgets col-auto">
                    <input id="specimen-date-estimated" type="checkbox" name="specimen_date_estimated" value="${pihAppsConfig.labOrderConfig.estimatedCollectionDateAnswer.uuid}" />
                    ${ui.message("pihapps.dateIsEstimated")}
                </span>
            </div>

            <% if (specimenCollectionEncounterRole) { %>
                <div id="specimen-provider-section" class="form-field-section row">
                    <span id="specimen-provider-label" class="form-field-label col-4">${ui.message("pihapps.specimenCollectedBy")}:</span>
                    <span id="specimen-provider-widgets" class="form-field-widgets col-auto">
                        ${ui.includeFragment("pihapps", "field/provider", [
                                id: "specimen-provider-picker",
                                initialValue: sessionContext.currentProvider,
                                formFieldName: "specimen_collection_provider",
                        ])}
                    </span>
                </div>
            <% } %>
            <div id="specimen-location-section" class="form-field-section row">
                <span id="specimen-location-label" class="form-field-label col-4">${ui.message("pihapps.specimenCollectionLocation")}:</span>
                <span id="specimen-location-widgets" class="form-field-widgets col-auto">
                    ${ui.includeFragment("pihapps", "field/location", [
                            id: "specimen-location-picker",
                            label: "",
                            valueField: "uuid",
                            initialValue: sessionContext.sessionLocation,
                            formFieldName: "specimen_collection_location",
                            "withTag": "Login Location"
                    ])}
                </span>
            </div>
            <div id="specimen-date-received-section" class="form-field-section row">
                <span id="specimen-date-received-label" class="form-field-label col-4">${ui.message("pihapps.specimenReceivedDate")}:</span>
                <span id="specimen-date-received-widgets" class="form-field-widgets col-auto">
                    ${ui.includeFragment("pihapps", "field/datetimepicker", [
                            id: "specimen-received-date-picker",
                            label: "",
                            formFieldName: "specimen_received_date",
                            useTime: true,
                            left: true
                    ])}
                </span>
            </div>
            <div id="lab-location-section" class="form-field-section row">
                <span id="lab-location-label" class="form-field-label col-4">${ui.message("pihapps.labTestLocation")}:</span>
                <span id="lab-location-widgets" class="form-field-widgets col-auto">
                    ${ui.includeFragment("uicommons", "field/dropDown", [
                            id: "test-location-picker",
                            label: "",
                            formFieldName: "test_location",
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

<div id="remove-orders-section" style="display:none;">

</div>