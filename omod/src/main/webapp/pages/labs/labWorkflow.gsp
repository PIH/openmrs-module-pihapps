<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
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

    const ordersTableInfo = {

        pageNumber: 0,
        pageSize: 10,
        totalCount: 0,

        setTotalCount(totalCount) {
            this.totalCount = totalCount;
        },

        hasPreviousRecords() {
            return this.pageNumber > 0;
        },

        hasNextRecords() {
            return this.getLastNumberForPage() < this.totalCount;
        },

        nextPage() {
            if (this.hasNextRecords()) {
                this.pageNumber++;
            }
        },

        previousPage() {
            if (this.hasPreviousRecords()) {
                this.pageNumber--;
            }
        },

        lastPage() {
            this.pageNumber = Math.ceil(this.totalCount / this.pageSize) - 1;
        },

        firstPage() {
            this.pageNumber = 0;
        },

        getPageNumber() {
            return this.startIndex / this.limit;
        },

        getFirstNumberForPage() {
            return this.pageNumber * this.pageSize + 1;
        },

        getLastNumberForPage() {
            const lastNumber = this.getFirstNumberForPage() + this.pageSize - 1
            return lastNumber > this.totalCount ? this.totalCount : lastNumber;
        },

        getInfoText() {
            return '${ ui.message("uicommons.dataTable.info") }'
                .replace('_START_', this.getFirstNumberForPage())
                .replace('_END_', this.getLastNumberForPage())
                .replace('_TOTAL_', this.totalCount);
        }
    };

    <% orderStatuses.each{ s -> %>
        window.translations['${s}'] = '${ui.message("pihapps.orderStatus." + s)}';
    <% } %>
    <% fulfillerStatuses.each{ s -> %>
        window.translations['${s}'] = '${ui.message("pihapps.fulfillerStatus." + s)}';
    <% } %>

    jq(document).ready(function() {
        // Create a datatable of the encounter data
        const ordersTable = jq("#orders-table").dataTable(
            {
                bFilter: false,
                bJQueryUI: true,
                iDisplayLength: ordersTableInfo.pageSize,
                bSort: false,
                bAutoWidth: false,
                sDom: 'ft<\"fg-toolbar ui-toolbar ui-corner-bl ui-corner-br ui-helper-clearfix datatables-info-and-pg \">',
                oLanguage: {
                    sZeroRecords: "${ ui.message("uicommons.dataTable.zeroRecords") }",
                    sEmptyTable: "${ ui.message("uicommons.dataTable.emptyTable") }",
                    sInfoEmpty: "${ ui.message("uicommons.dataTable.infoEmpty") }",
                    sLoadingRecords: "${ ui.message("uicommons.dataTable.loadingRecords") }",
                    sProcessing: "${ ui.message("uicommons.dataTable.processing") }"
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

        const getOrderDate = function(order) {
            const m = moment(order.dateActivated);
            if ( m.hour() === 0 && m.minute() === 0 && m.second() === 0 && m.millisecond() === 0) {
                return m.format(dateFormat);
            }
            return m.format(dateTimeFormat);
        }

        const getFulfillerStatus = function(order) {
            return window.translations[order.fulfillerStatus ?? 'RECEIVED'];
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

        const getLabTest = function(order) {
            return conceptUtils.getConceptShortName(order.concept, window.sessionContext?.locale);
        }

        // TODO: Review this endpoint for correctness, and fix/update as needed.  For now, the goal is consistency with legacy owa
        const fetchOrderData = function() {
            const endpoint = openmrsContextPath + "/ws/rest/v1/pihapps/labOrder";
            const filterParams = getFilters();
            const params = {
                ...filterParams,
                "totalCount": true,
                "v": orderRepresentation,
                "startIndex": ordersTableInfo.pageNumber * ordersTableInfo.pageSize,
                "limit": ordersTableInfo.pageSize,
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
                        orderRow.push(getOrderDate(order));
                        orderRow.push(order.accessionNumber);
                        orderRow.push(getOrderStatus(order));
                        orderRow.push(getFulfillerStatus(order));
                        orderRow.push(getLabTest(order));
                        tableRows.push(orderRow);
                    });
                }
                addInfoToOrdersTable(data);
                ordersTable.fnAddData(tableRows);
                ordersTable.fnDraw();
            });
        };

        const addInfoToOrdersTable = function(data) {
            if (!data || !data.results || data.results.length === 0) {
                ordersTableInfo.setTotalCount(0);
                ordersTableInfo.firstPage();
                jq("#orders-table-info-and-paging").hide();
                return;
            }
            ordersTableInfo.setTotalCount(data.totalCount);
            jq("#orders-table_info").html(ordersTableInfo.getInfoText());
            if (ordersTableInfo.hasPreviousRecords()) {
                jq("#orders-table_first").removeClass("ui-state-disabled");
                jq("#orders-table_previous").removeClass("ui-state-disabled");
            }
            else {
                jq("#orders-table_first").addClass("ui-state-disabled");
                jq("#orders-table_previous").addClass("ui-state-disabled");
            }
            if (ordersTableInfo.hasNextRecords()) {
                jq("#orders-table_next").removeClass("ui-state-disabled");
                jq("#orders-table_last").removeClass("ui-state-disabled");
            }
            else {
                jq("#orders-table_next").addClass("ui-state-disabled");
                jq("#orders-table_last").addClass("ui-state-disabled");
            }
            jq("#orders-table-info-and-paging").show();
        }

        jq("#test-filter-form").find(":input").change(function () {
            fetchOrderData();
        });

        jq("#orders-table_first").on("click", function() {
            ordersTableInfo.firstPage();
            fetchOrderData();
        });
        jq("#orders-table_previous").on("click", function() {
            ordersTableInfo.previousPage();
            fetchOrderData();
        });
        jq("#orders-table_next").on("click", function() {
            ordersTableInfo.nextPage();
            fetchOrderData();
        });
        jq("#orders-table_last").on("click", function() {
            ordersTableInfo.lastPage();
            fetchOrderData();
        });

        fetchOrderData();
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
    <tbody>

    </tbody>
</table>
<div id="orders-table-info-and-paging" class="fg-toolbar ui-toolbar ui-corner-bl ui-corner-br ui-helper-clearfix datatables-info-and-pg">
    <div class="dataTables_info" id="orders-table_info">
    </div>
    <div class="dataTables_paginate fg-buttonset ui-buttonset fg-buttonset-multi ui-buttonset-multi paging_full_numbers" id="orders-table_paginate">
        <a id="orders-table_first" class="first ui-corner-tl ui-corner-bl fg-button ui-button ui-state-default">${ ui.message("uicommons.dataTable.first") }</a>
        <a id="orders-table_previous" class="previous fg-button ui-button ui-state-default">${ ui.message("uicommons.dataTable.previous") }</a>
        <a id="orders-table_next" class="next fg-button ui-button ui-state-default">${ ui.message("uicommons.dataTable.next") }</a>
        <a id="orders-table_last" class="last ui-corner-tr ui-corner-br fg-button ui-button ui-state-default">${ ui.message("uicommons.dataTable.last") }</a>
    </div>
</div>