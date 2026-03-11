<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "conceptUtils.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def now = new Date()
    def patientListPage = ui.pageLink("pihapps", "labs/labPatientList")
    def orderLabsPage = ui.pageLink("coreapps", "findpatient/findPatient", ["app": "pih.app.labs.ordering"])
%>

<script type="text/javascript">
    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labOrderList")) }" , link: '${ui.pageLink("pihapps", "labs/labOrderList")}'}
    ];

    const patientRep = "(uuid,display,person:(display),identifiers:(identifier,preferred,identifierType:(uuid,display,auditInfo:(dateCreated))))";
    const conceptRep = "(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))";
    const orderRep = "id,uuid,display,orderNumber,dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,patient:(uuid,display,person:(display),identifiers:(identifier,preferred,identifierType:(uuid,display,auditInfo:(dateCreated)))),concept:" + conceptRep

    const labOrderConfigRep = "(labTestOrderType:(uuid),availableLabTestsByCategory:(category:" + conceptRep + ",labTests:" + conceptRep + "),orderStatusOptions:(status,display),fulfillerStatusOptions:(status,display),orderFulfillmentStatusOptions:(status,display),testLocationQuestion:(uuid,answers:(uuid,display)),specimenCollectionEncounterType:(uuid),specimenCollectionEncounterRole:(uuid),estimatedCollectionDateQuestion:(uuid),estimatedCollectionDateAnswer:(uuid),testOrderNumberQuestion:(uuid),labIdentifierConcept:(uuid),specimenReceivedDateQuestion:(uuid),reasonTestNotPerformedQuestion:(uuid,answers:(uuid,display)))";
    const pihAppsConfigRep = "dateFormat,dateTimeFormat,primaryIdentifierType:(uuid),labOrderConfig:" + labOrderConfigRep;

    moment.locale(window.sessionContext?.locale ?? 'en');

    const pagingDataTable = new PagingDataTable(jq);
    const conceptUtils = new PihAppsConceptUtils(jq);
    const patientUtils = new PihAppsPatientUtils(jq);

    const viewSpecimenEncounter = function(encounterUuid) {
        const encounterRep = "id,uuid,patient:" + patientRep + ",encounterDatetime,encounterType:(uuid),location:(uuid,display),encounterProviders:(provider:(uuid,display),encounterRole:(uuid,display)),obs:(uuid,concept:(uuid,datatype:(name)),value,comment,formNamespaceAndPath)";
        const rep = "encounter:(" + encounterRep + "),orders:(" + orderRep + ")";
        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + pihAppsConfigRep + ")", function(pihAppsConfig) {
            jq.get(openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders/" + encounterUuid + "?v=custom:(" + rep + ")", function (encAndOrders) {
                jq(".lab-emr-id").html(patientUtils.getPreferredIdentifier(encAndOrders.encounter.patient, pihAppsConfig.primaryIdentifierType?.uuid ?? ''));
                jq(".lab-patient-name").html(encAndOrders.encounter.patient.person.display);
                initializeSpecimenCollectionForm({
                    patientUuid: encAndOrders.encounter.patient.uuid,
                    orders: encAndOrders.orders,
                    encounter: encAndOrders.encounter,
                    pihAppsConfig: pihAppsConfig,
                    onSuccessFunction: () => { closeEncounterEdit(); pagingDataTable.updateTable(); }
                });
                openEncounterEdit();
            });
        });
    };

    const viewOrderNotPerformed = function(orderUuid) {
        const rep = orderRep + ",reasonOrderNotFulfilled:(uuid,concept:" + conceptRep + ",valueCoded:" + conceptRep + ")";
        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + pihAppsConfigRep + ")", function(pihAppsConfig) {
            jq.get(openmrsContextPath + "/ws/rest/v1/order/" + orderUuid + "?v=custom:(" + rep + ")", function (order) {
                jq(".lab-emr-id").html(patientUtils.getPreferredIdentifier(order.patient, pihAppsConfig.primaryIdentifierType?.uuid ?? ''));
                jq(".lab-patient-name").html(order.patient.person.display);
                initializeOrderNotFulfilledForm({
                    orders: [order],
                    reason: order.reasonOrderNotFulfilled,
                    pihAppsConfig: pihAppsConfig,
                    onSuccessFunction: () => { closeReasonNotPerformed(); pagingDataTable.updateTable(); }
                });
                openReasonNotPerformed();
            });
        });
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
    const openReasonNotPerformed = () => openSection("#edit-reason-not-performed-section");
    const closeReasonNotPerformed = () => closeSection("#edit-reason-not-performed-section");
    const openLabResults = () => openSection("#record-lab-results-section");
    const closeLabResults = () => closeSection("#record-lab-results-section");

    jq(document).ready(function() {

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + pihAppsConfigRep + ")", function(pihAppsConfig) {

            const primaryIdentifierType = pihAppsConfig.primaryIdentifierType?.uuid ?? '';
            const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);
            const orderFulfillmentStatusOptions = pihAppsConfig.labOrderConfig.orderFulfillmentStatusOptions;

            // Column functions
            const getEmrId = (order) => { return patientUtils.getPreferredIdentifier(order.patient, primaryIdentifierType); };
            const getPatientName = (order) => { return order.patient.person.display; }
            const getOrderDate = (order) => { return dateUtils.formatDateWithTimeIfPresent(order.dateActivated); };
            const getOrderNumber = (order) => { return order.orderNumber; }
            const getAccessionNumber = (order) => { return order.accessionNumber; }

            const getSelectCheckbox = (order) => {
                return '<input class="order-selector" type="checkbox" value="' + order.uuid + '" />';
            }

            const getLabTest = function(order) {
                const urgency = order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '';
                return urgency + conceptUtils.getConceptShortName(order.concept, window.sessionContext?.locale);
            }
            const getSpecimenDate = function(order) {
                const fulfillerEncounter = order.fulfillerEncounter;
                if (!fulfillerEncounter) {
                    return "";
                }
                const specimenDate = dateUtils.formatDateWithTimeIfPresent(fulfillerEncounter.encounterDatetime);
                return "<a href=\"javascript:viewSpecimenEncounter('" + fulfillerEncounter.uuid +  "')\">" + specimenDate + "</a>";
            };

            const getOrderFulfillmentStatus = (order) => {
                const statusDisplay = patientUtils.getOrderFulfillmentStatusOption(order, orderFulfillmentStatusOptions).display;
                if (order.fulfillerStatus === 'EXCEPTION') {
                    return "<a href=\"javascript:viewOrderNotPerformed('" + order.uuid +  "')\">" + statusDisplay + "</a>";
                }
                return statusDisplay;
            };

            const getSelectedOrders = function() {
                const orderUuids = [];
                jq("#orders-table").find(".order-selector:checked").each(function(index, element) {
                    orderUuids.push(jq(element).val())
                });
                return pagingDataTable.getRowObjects().filter((o) => orderUuids.includes(o.uuid));
            }

            const getFilterParameterValues = function() {
                return {
                    "orderType": pihAppsConfig.labOrderConfig.labTestOrderType?.uuid,
                    "patient": jq("#patient-filter-field").val(),
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
                representation: "custom:(id,uuid,display,orderNumber,dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),fulfillerEncounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,patient:" + patientRep + ",concept:" + conceptRep,
                parameters: { ...getFilterParameterValues() },
                columnTransformFunctions: [
                    getSelectCheckbox, getEmrId, getPatientName, getOrderNumber, getOrderDate, getSpecimenDate, getAccessionNumber, getOrderFulfillmentStatus, getLabTest
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

            jq("#specimen-encounter-section button.cancel").click((event) => {
                event.preventDefault();
                closeEncounterEdit();
            });

            jq("#select-all-orders").change(function () {
                if (jq(this).prop("checked")) {
                    jq(".order-selector").prop("checked", "checked");
                }
                else {
                    jq(".order-selector").removeAttr("checked");
                }
            });

            jq("#record-results-button").click(function() {
                const selectedOrders = getSelectedOrders();
                if (selectedOrders.length > 0) {
                    initializeLabResultsForm({
                        orders: selectedOrders,
                        pihAppsConfig: pihAppsConfig,
                        onSuccessFunction: () => {
                            document.location.href = patientListPage;
                        }
                    });
                    openLabResults();
                }
            });

            jq("#record-lab-results-section button.cancel").click((event) => {
                event.preventDefault();
                closeLabResults();
            });
        });
    });
</script>

<style>
    #edit-specimen-encounter-section { display: none; }
    #edit-reason-not-performed-section { display: none; }
    #record-lab-results-section { display: none; }
</style>

<div id="view-orders-section">
    <div class="row justify-content-between">
        <div class="col-6">
            <h3>${ ui.message("pihapps.labOrderList") }</h3>
        </div>
        <div class="col-6 text-right">
            <div class="action-menu dropdown show">
                <a class="btn btn-sm btn-secondary dropdown-toggle" href="#" role="button" id="dropdownMenuLink" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                    ${ ui.message("pihapps.actions") }
                </a>
                <div class="dropdown-menu dropdown-menu-right" aria-labelledby="dropdownMenuLink">
                    <a class="dropdown-item" href="${ patientListPage }">${ ui.message("pihapps.labPatientReception") }</a>
                    <a class="dropdown-item" href="${ orderLabsPage }">${ ui.message("pihapps.addLabOrders") }</a>
                </div>
            </div>
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
        <div class="row justify-content-start align-items-end">
            <div class="col-md-6 col-sm-6">
                <label for="patient-filter">${ ui.message("pihapps.patient") }</label>
                ${ ui.includeFragment("pihapps", "field/patient", [ id: "patient-filter", formFieldName: "patient" ]) }
            </div>
            <div class="col">
                <label for="lab-id-filter">${ ui.message("pihapps.labId") }:</label>
                <input id="lab-id-filter" type="text" name="labId" value=""/>
            </div>
        </div>
    </form>
    <table id="orders-table">
        <thead>
            <tr>
                <th><input id="select-all-orders" type="checkbox" /> ${ ui.message("pihapps.all") }</th>
                <th>${ ui.message("pihapps.emrId") }</th>
                <th>${ ui.message("pihapps.name") }</th>
                <th>${ ui.message("pihapps.orderNumber") }</th>
                <th>${ ui.message("pihapps.orderDate") }</th>
                <th>${ ui.message("pihapps.specimenDate") }</th>
                <th>${ ui.message("pihapps.labId") }</th>
                <th>${ ui.message("pihapps.orderFulfillmentStatus") }</th>
                <th>${ ui.message("pihapps.labTest") }</th>
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

    <div id="order-actions-section" style="padding-top: 20px;">
        <input type="button" id="record-results-button" value="${ ui.message("pihapps.recordLabResults") }" />
    </div>
</div>

<div id="edit-specimen-encounter-section">
    <div class="row justify-content-between">
        <div class="col-6">
            <h3>
                ${ ui.message("pihapps.specimenCollectionDetails") } -
                <span class="lab-patient-name"></span>
                (<span class="lab-emr-id"></span>)
            </h3>
        </div>
    </div>
    ${ ui.includeFragment("pihapps", "labs/specimenCollectionEncounter", ["id": "specimen-encounter-section"])}
</div>

<div id="edit-reason-not-performed-section">
    <div class="row justify-content-between">
        <div class="col-6">
            <h3>
                ${ ui.message("pihapps.removeSelectedOrders") } -
                <span class="lab-patient-name"></span>
                (<span class="lab-emr-id"></span>)
            </h3>
        </div>
    </div>
    ${ ui.includeFragment("pihapps", "labs/recordOrderNotFulfilled", ["id": "reason-not-performed-section"])}
</div>

<div id="record-lab-results-section">
    <div class="row justify-content-between">
        <div class="col-6">
            <h3>
                ${ ui.message("pihapps.recordLabResults") }
            </h3>
        </div>
    </div>
    ${ ui.includeFragment("pihapps", "labs/recordLabResults", ["id": "lab-results-section"])}
</div>