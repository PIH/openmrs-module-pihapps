<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def now = new Date()
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">

    const patientUuid = '${patient.patient.uuid}';

    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.escapeJs(ui.format(patient.patient)) }" , link: '${ui.urlBind("/" + contextPath + pihAppsConfig.getDashboardUrl(), ["patientId": patient.id])}'},
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labResults")) }" , link: '${ui.pageLink("pihapps", "labs/labPatientResults", ["patientId": patient.id])}'}
    ];

    moment.locale(window.sessionContext?.locale ?? 'en');

    const pagingDataTable = new PagingDataTable(jq);

    jq(document).ready(function() {

        const conceptRep = "id,uuid,datatype:(name),allowDecimal,units,display,displayStringForLab";
        const configRep =
            "dateFormat,dateTimeFormat,labOrderConfig:(" +
                "labResultCategoriesConceptSet:(" + conceptRep + ",setMembers:(" + conceptRep + ",setMembers:(" + conceptRep + ",setMembers:(" + conceptRep + "))))" +
            ")";

        const obsRep = "uuid,obsDatetime,concept:(" + conceptRep + "),obsGroup:(uuid,concept:(" + conceptRep + ")),valueCoded:(" + conceptRep + "),valueNumeric,valueDatetime,valueText,value,referenceRange"

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + configRep + ")", function(pihAppsConfig) {

            const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat)

            const getFilterParameterValues = function() {
                return {
                    "patient": patientUuid,
                    "labTest": jq("#testConcept-filter").val(),
                    "category": jq("#category-filter").val(),
                    "onOrAfter": jq("#onOrAfter-filter-field").val(),
                    "onOrBefore": jq("#onOrBefore-filter-field").val()
                }
            }

            const getDate = (obs) => { return dateUtils.formatDateWithTimeIfPresent(obs.obsDatetime); };
            const getLabTest = (obs) => { return obs.concept.displayStringForLab; };
            const getResults = (obs) => {
                const value = obs.valueCoded ? obs.valueCoded.displayStringForLab :
                              obs.valueNumeric ? (obs.valueNumeric + (obs.concept.units ? " " + obs.concept.units : "")) :
                              obs.valueDatetime ?  dateUtils.formatDateWithTimeIfPresent(obs.valueDatetime) :
                              obs.valueText ?? obs.value;
                if (obs.valueNumeric && obs.referenceRange) {
                    const refRange = obs.referenceRange;
                    if ((refRange.lowNormal && obs.valueNumeric < refRange.lowNormal) ||
                        (refRange.hiNormal && obs.valueNumeric > refRange.hiNormal)) {
                        return "<span class='abnormal-value'>(" + value + ")</span>";
                    }
                }
                return value;
            }
            const getNormalRange = (obs) => {
                const refRange = obs.referenceRange;
                if (refRange) {
                    if (refRange.lowNormal && refRange.hiNormal) {
                        return refRange.lowNormal + " - " + refRange.hiNormal;
                    }
                    if (refRange.lowNormal) {
                        return ">= " + refRange.lowNormal;
                    }
                    if (refRange.hiNormal) {
                        return "=< " + refRange.hiNormal;
                    }
                }
                return "";
            }

            const addGroupRows = () => {
                let displayedGroup = null;
                const tableRowObjects = pagingDataTable.getRowObjects();
                const tableRowData = pagingDataTable.getTableElement().find("tbody tr");
                tableRowObjects.forEach((obs, index) => {
                    const currentGroup = obs.obsGroup?.uuid;
                    if (currentGroup !== displayedGroup) {
                        if (currentGroup) {
                            const newRow = jq("<tr>").addClass("obs-group-row");
                            const newCell = jq("<td>").attr("colspan", pagingDataTable.columnTransformFunctions.length).html(obs.obsGroup.concept.displayStringForLab);
                            newRow.append(newCell);
                            newRow.insertBefore(tableRowData[index]);
                        }
                        displayedGroup = currentGroup;
                    }
                    if (currentGroup) {
                        const testNameCell = jq(tableRowData[index]).find("td")[0];
                        const contents = jq(testNameCell).addClass("obs-group-member-row");
                    }
                });
            }

            pagingDataTable.initialize({
                tableSelector: "#results-table",
                tableInfoSelector: "#results-table-info-and-paging",
                endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/labResults",
                representation: "custom:(" + obsRep + ")",
                parameters: { ...getFilterParameterValues() },
                columnTransformFunctions: [
                    getLabTest, getDate, getResults, getNormalRange
                ],
                tableUpdateCallback: addGroupRows,
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

            const testFilter = jq("#testConcept-filter");
            const categoryFilter = jq("#category-filter");

            pihAppsConfig.labOrderConfig.labResultCategoriesConceptSet.setMembers.forEach((categoryConcept) => {
                const optGroup = jq("<optGroup>").attr("label", categoryConcept.displayStringForLab).addClass("category-opt-group category-" + categoryConcept.uuid);
                categoryConcept.setMembers.forEach((testOrPanel) => {
                    const labOpt = jq("<option>").attr("value", testOrPanel.uuid).html(testOrPanel.displayStringForLab);
                    optGroup.append(labOpt);
                });
                testFilter.append(optGroup);
                categoryFilter.append(jq("<option>").attr("value", categoryConcept.uuid).html(categoryConcept.displayStringForLab));
            });
            categoryFilter.on("change", () => {
                const category = categoryFilter.val();
                if (category === "") {
                    testFilter.find(".category-opt-group").show();
                }
                else {
                    testFilter.val("");
                    testFilter.find(".category-opt-group").hide();
                    testFilter.find(".category-" + category).show();
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
    .abnormal-value {
        font-weight: bold;
        color: red;
    }
    .obs-group-row {
        text-decoration: underline;
    }
    .obs-group-member-row {
        padding-left: 20px;
    }
</style>
<div class="row justify-content-between" style="padding-top: 10px">
    <div class="col-6">
        <h3>${ ui.message("pihapps.labResults") }</h3>
    </div>
</div>
<form method="get" id="test-filter-form">
    <div class="row justify-content-start align-items-end">
        <div class="col">
            ${ ui.includeFragment("pihapps", "field/datetimepicker", [
                    id: "onOrAfter-filter",
                    formFieldName: "onOrAfter",
                    label: "pihapps.onOrAfter",
                    classes: "form-control",
                    endDate: now,
                    useTime: false,
                    clearButton: true
            ])}
        </div>
        <div class="col">
            ${ ui.includeFragment("pihapps", "field/datetimepicker", [
                    id: "onOrBefore-filter",
                    formFieldName: "onOrBefore",
                    label: "pihapps.onOrBefore",
                    classes: "form-control",
                    endDate: now,
                    useTime: false,
                    clearButton: true
            ])}
        </div>
        <div class="col">
            <label for="category-filter">${ ui.message("pihapps.category") }</label>
            <select id="category-filter" name="category" class="form-control">
                <option value=""></option>
            </select>
        </div>
        <div class="col">
            <label for="testConcept-filter">${ ui.message("pihapps.labTest") }</label>
            <select id="testConcept-filter" name="testConcept" class="form-control">
                <option value=""></option>
            </select>
        </div>
    </div>
</form>

<table id="results-table">
    <thead>
        <tr>
            <th>${ ui.message("pihapps.labTest") }</th>
            <th>${ ui.message("pihapps.resultDate") }</th>
            <th>${ ui.message("pihapps.results") }</th>
            <th>${ ui.message("pihapps.normalRange") }</th>
        </tr>
    </thead>
    <tbody></tbody>
</table>
<div id="results-table-info-and-paging" style="font-size: .9em">
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
