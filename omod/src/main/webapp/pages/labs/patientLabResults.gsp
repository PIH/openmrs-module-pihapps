<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeJavascript("pihapps", "labs/labResultFields.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def now = new Date()
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">

    const patientUuid = '${patient.patient.uuid}';
    const resultRendererExtensions = ${ ui.toJson(resultRendererExtensions) };

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
                "labResultCategoriesConceptSet:(" + conceptRep + ",setMembers:(" + conceptRep + ",setMembers:(" + conceptRep + ",setMembers:(" + conceptRep + "))))," +
                "collectResultComments,requireResultValidation" +
            ")";

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + configRep + ")", function(pihAppsConfig) {

            const collectComments = pihAppsConfig.labOrderConfig.collectResultComments;
            const obsRep = "uuid,obsDatetime,concept:(" + conceptRep + "),obsGroup:(uuid,concept:(" + conceptRep + "),obsGroup:(uuid,concept:(" + conceptRep + "))),order:(uuid),valueCoded:(" + conceptRep + "),valueNumeric,valueDatetime,valueText,value" + (collectComments ? ",comment" : "") + ",effectiveReferenceRange,creator:(display,person:(display))"

            // Obs groups whose result an extension renders, by concept uuid: they are returned as one obs, the group
            const resultRenderers = PihAppsLabResultFields.getResultRenderers(resultRendererExtensions);
            const getResultRenderer = (obs) => resultRenderers[obs.concept.uuid];

            const patientUtils = new PihAppsPatientUtils(jq);
            const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat)

            const getFilterParameterValues = function() {
                const panelVal = jq("#panel-filter").val();
                const testVal = jq("#testConcept-filter").val();
                return {
                    "patient": patientUuid,
                    "labTest": (testVal === "" ? panelVal : testVal),
                    "category": jq("#category-filter").val(),
                    "onOrAfter": jq("#onOrAfter-filter-field").val(),
                    "onOrBefore": jq("#onOrBefore-filter-field").val(),
                    // Clinicians only see results a validator has released
                    ...(pihAppsConfig.labOrderConfig.requireResultValidation ? { "status": ["FINAL", "AMENDED"] } : {}),
                    "renderedConcept": Object.keys(resultRenderers)
                }
            }

            const getDate = (obs) => {
                return dateUtils.formatDateWithTimeIfPresent(obs.obsDatetime);
            }

            const getLabTest = (obs) => {
                if (getResultRenderer(obs)) {
                    return jq("<span>").html(obs.concept.displayStringForLab).prop("outerHTML");
                }
                return jq("<a>").attr("href", "#").addClass("lab-trends-link").html(obs.concept.displayStringForLab).prop("outerHTML");
            }

            const getResults = (obs) => {
                if (getResultRenderer(obs)) {
                    return jq("<div>").addClass("result-rendered").prop("outerHTML");
                }
                return patientUtils.formatObsValue(obs, dateUtils);
            }

            const getNormalRange = (obs) => {
                return patientUtils.formatReferenceRange(obs.effectiveReferenceRange, obs.concept.units);
            }

            const getEnteredBy = (obs) => {
                return jq("<div>").text(obs.creator?.person?.display ?? obs.creator?.display ?? "").html();
            }

            const onTableUpdate = () => {
                const tableRowObjects = pagingDataTable.getRowObjects();
                const tableRowData = pagingDataTable.getTableElement().find("tbody tr");

                // Clicking lab test should open result trends view
                tableRowObjects.forEach((obs, index) => {
                    jq(tableRowData[index]).find(".lab-trends-link").on("click", () => {
                        openLabTrendsView(obs);
                    });
                });

                // Obs groups rendered by an extension
                tableRowObjects.forEach((obs, index) => {
                    const ext = getResultRenderer(obs);
                    if (!ext) return;
                    const container = jq(tableRowData[index]).find(".result-rendered");
                    PihAppsLabResultFields.renderResult(jq, ext, obs.uuid).then(content => {
                        container.empty().append(content);
                    }).catch(error => console.warn("Unable to render the result with " + ext.id, error));
                });

                // Group by date if selected
                const groupByDate = jq("#groupByDate-filter").is(":checked");
                if (groupByDate) {
                    let displayedDate = null;
                    tableRowObjects.forEach((obs, index) => {
                        const currentDate = dateUtils.formatAsDateWithoutTime(obs.obsDatetime);
                        if (currentDate !== displayedDate) {
                            const newRow = jq("<tr>").addClass("group-by-date-row");
                            const newCell = jq("<td>").attr("colspan", pagingDataTable.columnTransformFunctions.length).html(currentDate);
                            newRow.append(newCell);
                            newRow.insertBefore(tableRowData[index]);
                            displayedDate = currentDate;
                        }
                    });
                }
                // Group by panel after grouping by date.  Obs of the order of the panel that are not in its group, such as
                // groups rendered by an extension, are shown in the panel.  The results of a panel within a panel are
                // shown in the outer panel, under the name of the inner one.
                const panelGroup = (obs) => obs.obsGroup?.obsGroup ?? obs.obsGroup;
                const subPanelGroup = (obs) => obs.obsGroup?.obsGroup ? obs.obsGroup : null;
                let displayedGroup = null;
                let displayedSubGroup = null;
                let displayedGroupOrder = null;
                const alternateRowColor = "rgb(255, 253, 245)";
                let rowColor = null;
                tableRowObjects.forEach((obs, index) => {
                    const tableRow = jq(tableRowData[index]);
                    const inDisplayedGroupOrder = !obs.obsGroup && displayedGroup && obs.order && obs.order.uuid === displayedGroupOrder;
                    const currentGroup = inDisplayedGroupOrder ? displayedGroup : panelGroup(obs)?.uuid;
                    if (currentGroup !== displayedGroup) {
                        rowColor = (rowColor === "white" ? alternateRowColor : "white");
                        if (currentGroup) {
                            const newRow = jq("<tr>").addClass("obs-group-row").css("background-color", rowColor);
                            const newCell = jq("<td>").attr("colspan", pagingDataTable.columnTransformFunctions.length).html(panelGroup(obs).concept.displayStringForLab);
                            newRow.append(newCell);
                            newRow.insertBefore(tableRow);
                        }
                        displayedGroup = currentGroup;
                        displayedSubGroup = null;
                        displayedGroupOrder = obs.order?.uuid ?? null;
                    }
                    const currentSubGroup = subPanelGroup(obs);
                    if (currentGroup && (currentSubGroup?.uuid ?? null) !== displayedSubGroup) {
                        if (currentSubGroup) {
                            const subRow = jq("<tr>").addClass("obs-group-row obs-subgroup-row").css("background-color", rowColor);
                            subRow.append(jq("<td>").attr("colspan", pagingDataTable.columnTransformFunctions.length)
                                .addClass("obs-group-member-row").html(currentSubGroup.concept.displayStringForLab));
                            subRow.insertBefore(tableRow);
                        }
                        displayedSubGroup = currentSubGroup?.uuid ?? null;
                    }
                    else if (!currentGroup && !displayedGroup) {
                        rowColor = (rowColor === "white" ? alternateRowColor : "white");
                    }
                    if (currentGroup) {
                        const testNameCell = tableRow.find("td")[0];
                        jq(testNameCell).addClass(currentSubGroup ? "obs-subgroup-member-row" : "obs-group-member-row");
                    }
                    tableRow.css("background-color", rowColor);
                });


            }

            const beforeRecreateTable = () => {
                pagingDataTable.getTableElement().find(".obs-group-row").remove();
                pagingDataTable.getTableElement().find(".group-by-date-row").remove();
            }

            pagingDataTable.initialize({
                tableSelector: "#results-table",
                tableInfoSelector: "#results-table-info-and-paging",
                endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/labResults",
                representation: "custom:(" + obsRep + ")",
                parameters: { ...getFilterParameterValues() },
                columnTransformFunctions: [
                    getLabTest, getDate, getResults, getNormalRange, getEnteredBy
                ],
                tableUpdateCallback: onTableUpdate,
                beforeRecreateTableCallback: beforeRecreateTable,
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

            jq("#clear-all-filters").on("click", () => {
                jq("#test-filter-form").find(":input").val("");
                jq("#category-filter").change();
            });

            const openLabTrendsView = (obs) => {
                jq("#patient-lab-results-section").hide();
                initializeLabTrends({ patientUuid: patientUuid, obs: obs, pihAppsConfig: pihAppsConfig, onCloseFunction: () => {
                    jq("#lab-trends-section").hide()
                    jq("#patient-lab-results-section").show()
                }});
            };
        });

        jq("#cancel-button").click(() => {
            history.back();
        });
    });
</script>
<style>
    .abnormal-value {
        font-weight: bold;
        color: red;
    }
    .obs-group-row {
    }
    .obs-group-member-row {
        padding-left: 20px;
    }
    .obs-subgroup-member-row {
        padding-left: 40px;
    }
    #groupByDate-filter {
        margin-top:8px;
    }
    .group-by-date-row td {
        background-color: lightgrey;
    }
    #lab-trends-section {
        display: none;
    }
    .action-buttons {
        padding-top: 10px;
    }
</style>
<div id="patient-lab-results-section">
    <div class="row justify-content-between" style="padding-top: 10px">
        <div class="col-6">
            <h3>${ ui.message("pihapps.labResults") }</h3>
        </div>
    </div>
    <form method="get" id="test-filter-form">
        <div class="row justify-content-start align-items-end">
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
                <input id="groupByDate-filter" type="checkbox" value="true"/>
                <label for="groupByDate-filter">${ ui.message("pihapps.groupByDate") }</label>
            </div>
            <div class="col">
                <a id="clear-all-filters" href="#">${ ui.message("pihapps.resetFilters") }</a>
            </div>
        </div>
    </form>

    <table id="results-table">
        <thead>
            <tr>
                <th>${ ui.message("pihapps.labTest") }</th>
                <th>${ ui.message("pihapps.specimenDate") }</th>
                <th>${ ui.message("pihapps.results") }</th>
                <th>${ ui.message("pihapps.normalRange") }</th>
                <th>${ ui.message("pihapps.enteredBy") }</th>
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
    <div class="action-buttons">
        <button id="cancel-button" class="cancel action-button">${ ui.message("uicommons.return") }</button>
    </div>
</div>

${ ui.includeFragment("pihapps", "labs/patientLabTrends", ["id": "lab-trends-section"])}