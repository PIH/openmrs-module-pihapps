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

<script type="text/javascript">
    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labPatientList")) }" , link: '${ui.pageLink("pihapps", "labs/labPatientList")}'}
    ];

    const orderFulfillmentStatus = '${orderFulfillmentStatus.name()}';

    moment.locale(window.sessionContext?.locale ?? 'en');

    const pagingDataTable = new PagingDataTable(jq);

    jq(document).ready(function() {

        const conceptRep = "(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))";
        const labOrderConfigRep = "(labTestOrderType:(uuid),availableLabTestsByCategory:(category:" + conceptRep + ",labTests:" + conceptRep + "),orderStatusOptions:(status,display),fulfillerStatusOptions:(status,display),orderFulfillmentStatusOptions:(status,display))";
        const rep = "dateFormat,dateTimeFormat,primaryIdentifierType:(uuid),labOrderConfig:" + labOrderConfigRep;

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + rep + ")", function(pihAppsConfig) {

            const primaryIdentifierType = pihAppsConfig.primaryIdentifierType?.uuid ?? '';
            const conceptUtils = new PihAppsConceptUtils(jq);
            const patientUtils = new PihAppsPatientUtils(jq);

            // Column functions
            const getEmrId = (patientWithOrders) => { return patientUtils.getPreferredIdentifier(patientWithOrders.patient, primaryIdentifierType); };
            const getPatientName = (patientWithOrders) => { return patientWithOrders.patient.person.display; }
            const getLabTest = function(order) {
                const urgency = order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '';
                return urgency + conceptUtils.getConceptShortName(order.concept, window.sessionContext?.locale);
            }
            const getTestNames = (patientWithOrders) => {return patientWithOrders.orders.map((o) => getLabTest(o)).join(", ");};

            const getFilterParameterValues = function() {
                return {
                    "orderType": pihAppsConfig.labOrderConfig.labTestOrderType?.uuid,
                    "orderFulfillmentStatus": "AWAITING_FULFILLMENT",
                    "patient": jq("#patient-filter-field").val(),
                    "sortBy": "dateActivated-desc"  // TODO: Sorting by dateActivated desc does not seem right, but doing this to match existing labWorkflow, but shouldn't this order by urgency and asc?
                }
            }

            const patientRep = "(uuid,display,person:(display),identifiers:(identifier,preferred,identifierType:(uuid,display,auditInfo:(dateCreated))))";
            const orderRep = "(id,uuid,display,orderNumber,dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,concept:(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType)))";

            pagingDataTable.initialize({
                tableSelector: "#orders-table",
                tableInfoSelector: "#orders-table-info-and-paging",
                endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/patientsWithOrders",
                representation: "custom:patient:" + patientRep + ",orders:" + orderRep,
                parameters: { ...getFilterParameterValues() },
                columnTransformFunctions: [
                    getEmrId, getPatientName, getTestNames
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
</style>

<div class="row justify-content-between">
    <div class="col-6">
        <h3>${ ui.message("pihapps.labPatientList") }</h3>
    </div>
</div>
<form method="get" id="test-filter-form">
    <div class="row justify-content-start align-items-end">
        <div class="col-md-6 col-sm-6">
            <label for="patient-filter">${ ui.message("pihapps.patient") }</label>
            ${ ui.includeFragment("pihapps", "field/patient", [ id: "patient-filter", formFieldName: "patient" ]) }
        </div>
    </div>
</form>
<table id="orders-table">
    <thead>
        <tr>
            <th>${ ui.message("pihapps.emrId") }</th>
            <th>${ ui.message("pihapps.name") }</th>
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
