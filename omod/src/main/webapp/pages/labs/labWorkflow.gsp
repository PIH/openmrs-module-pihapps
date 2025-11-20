<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment.min.js")

    def testsByCategory = labOrderConfig.getAvailableLabTestsByCategory()
    def now = new Date()
%>

<script type="text/javascript">
    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labWorkflow")) }" , link: '${ui.pageLink("pihapps", "labs/labWorkflow")}'}
    ];

    const primaryIdentifierType = '${pihAppsConfig.primaryIdentifierType?.uuid ?: ""}';

    jq(document).ready(function() {
        // Create a datatable of the encounter data
        const ordersTable = jq("#orders-table").dataTable(
            {
                bFilter: false,
                bJQueryUI: true,
                bLengthChange: true,
                iDisplayLength: 10,
                sPaginationType: 'full_numbers',
                bSort: false,
                sDom: 'ft<\"fg-toolbar ui-toolbar ui-corner-bl ui-corner-br ui-helper-clearfix datatables-info-and-pg \"ip>',
                oLanguage: {
                    oPaginate: {
                        sFirst: "${ ui.message("uicommons.dataTable.first") }",
                        sLast: "${ ui.message("uicommons.dataTable.last") }",
                        sNext: "${ ui.message("uicommons.dataTable.next") }",
                        sPrevious: "${ ui.message("uicommons.dataTable.previous") }"
                    },
                    sInfo: "${ ui.message("uicommons.dataTable.info") }",
                    sSearch: "${ ui.message("uicommons.dataTable.search") }",
                    sZeroRecords: "${ ui.message("uicommons.dataTable.zeroRecords") }",
                    sEmptyTable: "${ ui.message("uicommons.dataTable.emptyTable") }",
                    sInfoFiltered: "${ ui.message("uicommons.dataTable.infoFiltered") }",
                    sInfoEmpty: "${ ui.message("uicommons.dataTable.infoEmpty") }",
                    sLengthMenu: "${ ui.message("uicommons.dataTable.lengthMenu") }",
                    sLoadingRecords: "${ ui.message("uicommons.dataTable.loadingRecords") }",
                    sProcessing: "${ ui.message("uicommons.dataTable.processing") }",
                    oAria: {
                        sSortAscending: "${ ui.message("uicommons.dataTable.sortAscending") }",
                        sSortDescending: "${ ui.message("uicommons.dataTable.sortDescending") }"
                    }
                }
            }
        );

        const orderRepresentation = "custom:(id,uuid,display,orderNumber,dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,patient:(uuid,display,person:(display),identifiers:(identifier,preferred,identifierType:(uuid,display,auditInfo:(dateCreated)))),concept:(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))";

        const getFilters = function() {
            return {
                "patient": jq("#patient-filter-field").val(),
                "labTest": jq("#testConcept-filter").val(),
                "activatedOnOrAfter": jq("#orderedFrom-filter-field").val(),
                "activatedOnOrBefore": jq("#orderedTo-filter-field").val(),
                "accessionNumber": jq("#lab-id-filter").val(),
                "orderStatus": jq("#orderStatus-filter").val(),
                "fulfillerStatus": jq("#fulfillerStatus-filter").val()
            }
        }

        const getEmrId = function(order) {
            const sortedIdentifiers = order.patient.identifiers.sort(function(a, b) {
               const aPrimary = a.identifierType.uuid = primaryIdentifierType;
               const bPrimary = b.identifierType.uuid = primaryIdentifierType;
               if (aPrimary && !bPrimary) {
                   return -1;
               }
               if (!aPrimary && bPrimary) {
                   return 1;
               }
               if (a.preferred && !b.preferred) {
                   return -1;
               }
               if (!a.preferred && b.preferred) {
                   return 1;
               }
               const aCreated = new Date(a.identifierType.auditInfo.dateCreated).getTime();
               const bCreated = new Date(b.identifierType.auditInfo.dateCreated).getTime();
               return aCreated - bCreated;
            });
            return sortedIdentifiers[0].identifier;
        }

        const getPatientName = function(order) {
            return order.patient.person.display;
        }

        const formatDate = function(dateString) {
            return moment(dateString).format("DD-MMM-YYYY");
        }

        // TODO: Review this endpoint for correctness, and fix/update as needed.  For now, the goal is consistency with legacy owa
        const fetchOrderData = function() {
            const endpoint = openmrsContextPath + "/ws/rest/v1/pihapps/labOrder";
            const filterParams = getFilters();
            const params = {
                ...filterParams,
                "totalCount": true,
                "v": orderRepresentation,
                "sortBy": "dateActivated-desc" // TODO: Adding this to match existing labworkflow behavior, but shouldn't this order by urgency and asc?
            }
            // TODO: Look at paging, also updating only changed values, etc
            jq.get(endpoint, params, function(data) {
                ordersTable.fnClearTable();
                const tableRows = [];
                if (data && data.results) {
                    data.results.forEach((order) => {
                        const orderRow = [];
                        orderRow.push(getEmrId(order));
                        orderRow.push(getPatientName(order));
                        orderRow.push(order.orderNumber);
                        orderRow.push(formatDate(order.dateActivated));
                        orderRow.push(order.accessionNumber);
                        tableRows.push(orderRow);
                    });
                }
                ordersTable.fnAddData(tableRows);
                ordersTable.fnDraw();
            });
        }

        jq("#test-filter-form").find(":input").change(function() {
            fetchOrderData();
        });

        fetchOrderData();
    });
</script>

<style>
    #test-filter-form {
        padding-bottom: 20px;
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
</style>

<div class="row justify-content-between">
    <div class="col-6">
        <h3>${ ui.message("pihapps.labWorkflow") }</h3>
    </div>
    <div class="col-6 text-right">
        <a href="${ui.pageLink("coreapps", "findpatient/findPatient", ["app": "pih.app.labs.ordering"])}">
            <input type="button" value="${ui.message("pihapps.addLabOrders")}" style="max-width: unset;"/>
        </a>
    </div>
</div>
<form method="get" id="test-filter-form">
    <div class="row justify-content-start align-items-end">
        <div class="col">
            ${ ui.includeFragment("uicommons", "field/datetimepicker", [
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
            ${ ui.includeFragment("uicommons", "field/datetimepicker", [
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
            <label for="lab-id-filter">${ ui.message("pihapps.labId") }:</label>
            <input id="lab-id-filter" type="text" name="labId" value=""/>
        </div>
        <div class="col">
            <label for="testConcept-filter">${ ui.message("pihapps.labTest") }</label>
            <select id="testConcept-filter" name="testConcept" class="form-control">
                <option value=""></option>
                <% testsByCategory.keySet().forEach { category -> %>
                <optgroup label="${ labOrderConfig.formatConcept(category) }">
                    <% testsByCategory.get(category).forEach{ c -> %>
                    <option value="${c.id}">${labOrderConfig.formatConcept(c)}</option>
                    <% } %>
                </optgroup>
                <% } %>
            </select>
        </div>
    </div>
    <div class="row justify-content-start align-items-end">
        <div class="col-md-6 col-sm-6">
            <label for="patient-filter">${ ui.message("pihapps.patient") }</label>
            ${ ui.includeFragment("pihapps", "field/patient", [ id: "patient-filter", formFieldName: "patient" ]) }
        </div>
        <div class="col">
            <label for="orderStatus-filter">${ ui.message("pihapps.orderStatus") }</label>
            <select id="orderStatus-filter" name="orderStatus" class="form-control">
                <option value="">${ ui.message("pihapps.all") }</option>
                <% orderStatuses.each { orderStatus -> %>
                    <option value="${orderStatus.name()}">${ ui.message("pihapps.orderStatus." + orderStatus)}</option>
                <% } %>
            </select>
        </div>
        <div class="col">
            <label for="fulfillerStatus-filter">${ ui.message("pihapps.fulfillerStatus") }</label>
            <select id="fulfillerStatus-filter" name="fulfillerStatus" class="form-control">
                <option value="">${ ui.message("pihapps.all") }</option>
                <% fulfillerStatuses.each { fulfillerStatus -> %>
                    <option value="${fulfillerStatus.name()}">${ ui.message("pihapps.fulfillerStatus." + fulfillerStatus)}</option>
                <% } %>
            </select>
        </div>
    </div>
</form>
<table id="orders-table">
    <thead>
        <tr>
            <th>${ ui.message("pihapps.emrId") }</th>
            <th>${ ui.message("pihapps.name") }</th>
            <th>${ ui.message("pihapps.orderNumber") }</th>
            <th>${ ui.message("pihapps.orderDate") }</th>
            <th>${ ui.message("pihapps.labId") }</th>
        </tr>
    </thead>
    <tbody>

    </tbody>
</table>