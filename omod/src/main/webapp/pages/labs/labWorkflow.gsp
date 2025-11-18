<%
    ui.decorateWith("appui", "standardEmrPage")
    def testsByCategory = labOrderConfig.getAvailableLabTestsByCategory()
    def now = new Date()
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labWorkflow")) }" , link: '${ui.pageLink("pihapps", "labs/labWorkflow")}'}
    ];
</script>

<style>
    #test-filter-form {
        padding-bottom: 10px;
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
    <div class="col-12">
        <h3>${ ui.message("pihapps.labWorkflow") }</h3>
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
                <option value="active">${ ui.message("pihapps.active") }</option>
                <option value="expired">${ ui.message("pihapps.expired") }</option>
                <option value="discontinued">${ ui.message("pihapps.discontinued") }</option>
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
<table id="active-orders-list">
    <thead>
        <tr>
            <th>${ ui.message("pihapps.emrId") }</th>
            <th>${ ui.message("pihapps.name") }</th>
            <th>${ ui.message("pihapps.orderNumber") }</th>
            <th>${ ui.message("pihapps.orderDate") }</th>
            <th>${ ui.message("pihapps.labId") }</th>
            <th>${ ui.message("pihapps.status")} </th>
            <th>${ ui.message("pihapps.labTest") }</th>
            <th></th>
        </tr>
    </thead>
    <tbody>
    </tbody>
</table>