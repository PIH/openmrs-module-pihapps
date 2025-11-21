<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "conceptUtils.js")

    def testsByCategory = labOrderConfig.getAvailableLabTestsByCategory()
    def now = new Date()
%>

<script type="text/javascript">
    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labWorkflow")) }" , link: '${ui.pageLink("pihapps", "labs/labWorkflow")}'}
    ];

    moment.locale(window.sessionContext?.locale ?? 'en');
    const dateFormat = '${pihAppsConfig.getGlobalProperty("uiframework.formatter.JSdateFormat", "DD-MMM-YYYY")}';
    const dateTimeFormat = '${pihAppsConfig.getGlobalProperty("uiframework.formatter.JSdateAndTimeFormat", "DD-MMM-YYYY HH:mm")}';
    const primaryIdentifierType = '${pihAppsConfig.primaryIdentifierType?.uuid ?: ""}';
    const conceptUtils = new PihAppsConceptUtils(jq);

    <% orderStatuses.each{ s -> %>
        window.translations['${s}'] = '${ui.message("pihapps.orderStatus." + s)}';
    <% } %>
    <% fulfillerStatuses.each{ s -> %>
        window.translations['${s}'] = '${ui.message("pihapps.fulfillerStatus." + s)}';
    <% } %>

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

    const getOrderDate = function(order) {
        const m = moment(order.dateActivated);
        if ( m.hour() === 0 && m.minute() === 0 && m.second() === 0 && m.millisecond() === 0) {
            return m.format(dateFormat);
        }
        return m.format(dateTimeFormat);
    }

    const getOrderNumber = function(order) {
        return order.orderNumber;
    }

    const getAccessionNumber = function(order) {
        return order.accessionNumber;
    }

    const getOrderStatus = function(order) {
        if (order.dateStopped) {
            return window.translations['STOPPED'];
        }
        if (order.autoExpireDate && moment(order.autoExpireDate).isBefore(new Date())) {
            return window.translations['EXPIRED'];
        }
        return window.translations['ACTIVE'];
    }

    const getFulfillerStatus = function(order) {
        return window.translations[order.fulfillerStatus ?? 'RECEIVED'];
    }

    const getLabTest = function(order) {
        const urgency = order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '';
        return urgency + conceptUtils.getConceptShortName(order.concept, window.sessionContext?.locale);
    }

    jq(document).ready(function() {

        const getFilterParameterValues = function() {
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

        // TODO: Sorting by dateActivated desc does not seem right, but doing this to match existing labWorkflow, but shouldn't this order by urgency and asc?,
        const pagingDataTable = new PagingDataTable(jq, {
            tableSelector: "#orders-table",
            tableInfoSelector: "#orders-table-info-and-paging",
            endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/labOrder",
            representation: "custom:(id,uuid,display,orderNumber,dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,patient:(uuid,display,person:(display),identifiers:(identifier,preferred,identifierType:(uuid,display,auditInfo:(dateCreated)))),concept:(id,uuid,allowDecimal,display,names:(id,uuid,name,locale,localePreferred,voided,conceptNameType))",
            parameters: { ...getFilterParameterValues(), "sortBy": "dateActivated-desc" },
            columnTransformFunctions: [
                getEmrId, getPatientName, getOrderNumber, getOrderDate, getAccessionNumber, getOrderStatus, getFulfillerStatus, getLabTest
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
        pagingDataTable.initialize();

        jq("#test-filter-form").find(":input").change(function () {
            pagingDataTable.setParameters(getFilterParameterValues())
            pagingDataTable.goToFirstPage();
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
            <th>${ ui.message("pihapps.orderStatus") }</th>
            <th>${ ui.message("pihapps.fulfillerStatus") }</th>
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
