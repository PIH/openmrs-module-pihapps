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
                const panelVal = jq("#panel-filter").val();
                const testVal = jq("#testConcept-filter").val();
                return {
                    "patient": patientUuid,
                    "labTest": (testVal === "" ? panelVal : testVal),
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

            const categories = [];

            pihAppsConfig.labOrderConfig.labResultCategoriesConceptSet.setMembers.forEach((categoryConcept) => {

                const panels = [];
                const tests = [];

                categories.push({
                    value: categoryConcept.uuid,
                    display: categoryConcept.displayStringForLab,
                    panels: panels,
                    tests: tests
                });

                categoryConcept.setMembers.forEach((testOrPanel) => {
                    if (testOrPanel.setMembers && testOrPanel.setMembers.length > 0) {
                        panels.push({
                            value: testOrPanel.uuid,
                            display: testOrPanel.displayStringForLab,
                        });
                        testOrPanel.setMembers.forEach(test => {
                            let labTest = tests.find(t => t.value === test.uuid);
                            if (!labTest) {
                                labTest = {
                                    value: test.uuid,
                                    display: test.displayStringForLab,
                                    panels: []
                                }
                                tests.push(labTest);
                            }
                            labTest.panels.push(testOrPanel.uuid);
                        });
                    } else {
                        let labTest = tests.find(t => t.value === testOrPanel.uuid);
                        if (!labTest) {
                            labTest = {
                                value: testOrPanel.uuid,
                                display: testOrPanel.displayStringForLab,
                                panels: []
                            }
                            tests.push(labTest);
                        }
                    }
                });
                const conceptComparator = (a, b) => {return a.display.localeCompare(b.display, undefined, 'base')};
                panels.sort(conceptComparator);
                tests.sort(conceptComparator);
            });

            const categoryFilter = jq("#category-filter");
            const panelFilter = jq("#panel-filter");
            const testFilter = jq("#testConcept-filter");

            categories.forEach(c => {
                categoryFilter.append(jq("<option>").attr("value", c.value).html(c.display));
                const panelGroup = jq("<optGroup>").attr("label", c.display).addClass("category-opt-group category-" + c.value);
                panelFilter.append(panelGroup);
                const testGroup = jq("<optGroup>").attr("label", c.display).addClass("category-opt-group category-" + c.value);
                testFilter.append(testGroup);
                c.panels.forEach(p => {
                    panelGroup.append(jq("<option>").attr("value", p.value).html(p.display));
                });
                c.tests.forEach(t => {
                    const testOption = jq("<option>").attr("value", t.value).html(t.display).addClass("test-option");
                    t.panels.forEach(p => {
                        testOption.addClass("panel-option-" + p);
                    });
                    testGroup.append(testOption);
                });
            });

            const showAllTestsAndCategories = () => {
                panelFilter.find(".category-opt-group").show();
                testFilter.find(".category-opt-group").show();
                testFilter.find(".test-option").show();
            }

            const filterByCategory = () => {
                const category = categoryFilter.val();
                if (category) {
                    panelFilter.find(".category-opt-group").hide();
                    panelFilter.find(".category-" + category).show();
                    testFilter.val("");
                    testFilter.find(".category-opt-group").hide();
                    testFilter.find(".category-" + category).show();
                }
            }

            categoryFilter.on("change", () => {
                showAllTestsAndCategories();
                filterByCategory();
                panelFilter.val("");
            });

            panelFilter.on("change", () => {
                const panel = panelFilter.val();
                showAllTestsAndCategories();
                filterByCategory();
                if (panel) {
                    testFilter.val("");
                    testFilter.find(".category-opt-group").hide();
                    testFilter.find(".test-option").hide();
                    categories.forEach(c => {
                        const matchesInCategory = testFilter.find(".category-" + c.value).find(".panel-option-" + panel);
                        if (matchesInCategory.length > 0) {
                            testFilter.find(".category-" + c.value).show();
                            matchesInCategory.show();
                        }
                    });
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
            <label for="panel-filter">${ ui.message("pihapps.panel") }</label>
            <select id="panel-filter" name="panelConcept" class="form-control">
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
