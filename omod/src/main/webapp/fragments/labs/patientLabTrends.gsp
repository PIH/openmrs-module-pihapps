<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")

    config.require("id")
    def id = config.id
%>
<script type="text/javascript">
    function initializeLabTrends({ patientUuid, obs, pihAppsConfig, onCloseFunction }) {

        // Initialize core variables
        moment.locale(window.sessionContext?.locale ?? 'en');
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);
        const patientUtils = new PihAppsPatientUtils(jq);
        const parentElementId = '${id}';
        const parentElement = jq("#" + parentElementId);

        // Populate data
        parentElement.find(".lab-test-name").html(obs.concept.displayStringForLab);

        const conceptRep = "id,uuid,datatype:(name),allowDecimal,units,display,displayStringForLab";
        const obsRep = "uuid,obsDatetime,concept:(" + conceptRep + "),obsGroup:(uuid,concept:(" + conceptRep + ")),valueCoded:(" + conceptRep + "),valueNumeric,valueDatetime,valueText,value,referenceRange"

        const labTrendsTable = new PagingDataTable(jq);
        labTrendsTable.initialize({
            tableSelector: "#lab-result-trends-table",
            tableInfoSelector: "#lab-result-trends-table-info-and-paging",
            endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/labResults",
            representation: "custom:(" + obsRep + ")",
            parameters: { patient: patientUuid, labTest: obs.concept.uuid },
            columnTransformFunctions: [
                (obs) => { return dateUtils.formatDateWithTimeIfPresent(obs.obsDatetime) },
                (obs) => { return patientUtils.formatObsValue(obs, dateUtils) },
                (obs) => { return patientUtils.formatReferenceRange(obs.referenceRange, obs.concept.units) }
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

        // Set up cancel callback and display
        jq(".cancel.action-button").on("click", () => {
            parentElement.hide();
            onCloseFunction();
        });
        parentElement.show();
    }
</script>
<div id="${id}">
    <div class="row justify-content-between" style="padding-top: 10px">
        <div class="col-12">
            <h3>${ ui.message("pihapps.labResults") } - <span class="lab-test-name"></span></h3>
        </div>
    </div>
    <div class="row justify-content-between" style="padding-top: 10px">
        <div class="col-6">
            <table id="lab-result-trends-table">
                <thead>
                    <tr>
                        <th class="result-date-column-header">${ ui.message("pihapps.resultDate") }</th>
                        <th class="result-column-header">${ ui.message("pihapps.results") }</th>
                        <th class="normal-range-column-header">${ ui.message("pihapps.normalRange") }</th>
                    </tr>
                </thead>
                <tbody></tbody>
            </table>
            <div id="lab-result-trends-table-info-and-paging" style="font-size: .9em">
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
        </div>
        <div class="col-6">
            <div class="lab-result-trends-graph"></div>
        </div>
    </div>
    <div class="cancel-buttons">
        <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>
