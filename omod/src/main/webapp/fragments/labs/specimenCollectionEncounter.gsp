<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "conceptUtils.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")

    def now = new Date()

    config.require("id")
    def id = config.id
%>

<script type="text/javascript">

    const defaultOrderer = '${sessionContext.currentProvider.uuid}';
    const defaultLocation = '${sessionContext.sessionLocation.uuid}'

    moment.locale(window.sessionContext?.locale ?? 'en');

    function initializeSpecimenCollectionForm(formConfig) {

        const encounter = formConfig.encounter;
        const orders = formConfig.orders;
        const pihAppsConfig = formConfig.pihAppsConfig;

        const conceptUtils = new PihAppsConceptUtils(jq);
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);

        const ordersWidgetsSection = parentElement.find(".orders-widgets");
        ordersWidgetsSection.html("");
        if (orders.length > 0) {

            // Display the orders that are included at the top of the form, read-only
            let headerRow = jq("<div>").addClass("row table-header");
            headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.labTest") }"));
            headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.orderDate") }"));
            headerRow.append(jq("<div>").addClass("col-4").html("${ ui.message("pihapps.orderNumber") }"));
            ordersWidgetsSection.append(headerRow);
            orders.forEach((o) => {
                console.log(o);
                const urgency = o.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '';
                const labTest = urgency + conceptUtils.getConceptShortName(o.concept, window.sessionContext?.locale);
                let row = jq("<div>").addClass("row");
                row.append(jq("<div>").addClass("col-4").html(labTest));
                row.append(jq("<div>").addClass("col-4").html(dateUtils.formatDateWithTimeIfPresent(o.dateActivated)));
                row.append(jq("<div>").addClass("col-4").html(o.orderNumber));
                ordersWidgetsSection.append(row);
            });

            // Populate default values each time form is opened
            parentElement.find(".errors-section").html("");
            parentElement.find(":input").val("");
            const currentDatetime = dateUtils.roundDownToNearestMinuteInterval(new Date(), 5);
            parentElement.find(".specimen-date-estimated").attr("value", pihAppsConfig.labOrderConfig.estimatedCollectionDateAnswer.uuid).removeAttr("checked");
            jq(selectorPrefix + "specimen-date-picker-wrapper").datetimepicker("option", "maxDateTime", currentDatetime);
            jq(selectorPrefix + "specimen-date-picker-wrapper").datetimepicker("setDate", currentDatetime);
            jq(selectorPrefix + "specimen-location-picker-field").val(defaultLocation);

            const testLocationQuestion = pihAppsConfig.labOrderConfig.testLocationQuestion;
            if (!testLocationQuestion || testLocationQuestion.answers.length === 0) {
                parentElement.find(".lab-location-section").hide();
            }
            else {
                testLocationQuestion.answers?.forEach((answer) => {
                    jq(selectorPrefix + "test-location-picker-field").append(jq("<option>").attr("value", answer.uuid).html(answer.display));
                });
            }

            const encounterRole = pihAppsConfig.labOrderConfig.specimenCollectionEncounterRole?.uuid;
            if (!encounterRole) {
                parentElement.find(".specimen-provider-section").hide();
            }

            const getFieldValue = function(formData, fieldName) {
                return formData.find(e => e.name === fieldName)?.value ?? '';
            }

            const getObs = function(formData, fieldName, concept) {
                return {
                    concept: concept,
                    value: getFieldValue(formData, fieldName),
                    formNamespaceAndPath: 'pihapps^' + fieldName,
                    comment: 'result-entry-form^' + fieldName // This is here for backwards-compatibility with the labworkflow owa
                }
                // Note: In the labworkflow owa version, order was set on obs, but we do not do this here as there could be multiple orders
            }

            const formElement = parentElement.find("form");
            formElement.submit((event) => {
                event.preventDefault();
                parentElement.find(".action-button").attr("disabled", "disabled");
                const formData = formElement.serializeArray();

                parentElement.find(".errors-section").html("");
                const errors = [];
                const currentDate = moment();
                const collectionDateStr =  getFieldValue(formData, "specimen_collection_date");
                const collectionDate = collectionDateStr ? moment(collectionDateStr) : null;
                if (collectionDate && collectionDate.isAfter(currentDate)) {
                    errors.push('${ ui.message("pihapps.specimenCollectionDateCannotBeFuture") }');
                }
                const receivedDateStr = getFieldValue(formData, "specimen-received-date");
                const receivedDate = receivedDateStr ? moment(receivedDateStr) : null;
                if (receivedDate && receivedDate.isAfter(currentDate)) {
                    errors.push('${ ui.message("pihapps.specimenReceivedDateCannotBeFuture") }');
                }
                if (collectionDate && receivedDate && collectionDate.isAfter(receivedDate)) {
                    errors.push('${ ui.message("pihapps.specimenReceivedCannotBeBeforeCollected") }');
                }
                if (errors && errors.length > 0) {
                    errors.forEach(e => {
                        jq("#process-orders-errors-section").append(jq("<div>").html(e));
                    });
                    jq(".action-button").removeAttr("disabled");
                    return;
                }

                const provider = formData.find(e => e.name === "specimen_collection_provider")?.value;
                const encounterProviders = (provider && encounterRole) ? [{ provider, encounterRole }] : [];

                const orderNumberObs = orders.map((o, index) => {
                    return {
                        concept: pihAppsConfig.labOrderConfig.testOrderNumberQuestion.uuid,
                        value: o.orderNumber,
                        comment: "result-entry-form^test-order-number", // This is here for backwards-compatibility with labworkflow owa
                        formNamespaceAndPath: "pihapps^order_number_" + index
                    }
                });

                const encounterFulfillingOrders = {
                    encounter: {
                        patient: patientUuid,
                        encounterDatetime: formData.find(e => e.name === "specimen_collection_date").value,
                        encounterType: pihAppsConfig.labOrderConfig.specimenCollectionEncounterType?.uuid,
                        location: formData.find(e => e.name === "specimen_collection_location").value,
                        encounterProviders: encounterProviders,
                        obs: [
                            ...orderNumberObs,
                            getObs(formData, "lab-id", pihAppsConfig.labOrderConfig.labIdentifierConcept.uuid),
                            getObs(formData, "estimated-checkbox", pihAppsConfig.labOrderConfig.estimatedCollectionDateQuestion.uuid),
                            getObs(formData, "specimen-received-date", pihAppsConfig.labOrderConfig.specimenReceivedDateQuestion.uuid),
                            getObs(formData, "test-location-dropdown", pihAppsConfig.labOrderConfig.testLocationQuestion.uuid)
                        ].filter(o => o.value)
                    },
                    orders: orders.map(o => o.uuid)
                }
                jq.ajax({
                    url: openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders",
                    type: "POST",
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify(encounterFulfillingOrders),
                    dataType: "json",
                    success: () => {
                        document.location.href = patientListPage;
                    },
                    error: (xhr) => {
                        parentElement.find(".action-button").removeAttr("disabled");
                        const error = xhr?.responseJSON?.error ?? xhr?.responseJSON;
                        const message = error?.translatedMessage ?? error.message ?? error;
                        parentElement.find(".errors-section").html(message);
                    }
                });
            });

            parentElement.show();
        }
    }
</script>

<style>
    .form-field-label {
        line-height: 3;
    }
    .form-field-widgets {
        padding-left: 20px;
    }
    .form-field-widgets label {
        display: none;
    }
    .form-field-widgets p {
        display: inline;
    }
    .dialog select option {
        font-size: 1.0em;
    }
    form input, form select, form textarea, .form input, .form select, .form textarea {
        min-width: auto;
        width: fit-content;
    }
    .table-header div {
        font-weight: bold;
    }
    .errors-section {
        font-weight: bold;
        color: red;
    }
    .orders-section {
        padding: 10px 0 10px 0;
        background-color: lightgray;
        margin-bottom: 10px;
        padding-left: 5px;
    }
</style>
<div id="${id}">
    <div class="errors-section"></div>
    <form>
        <div class="dialog-content form">
            <div class="orders-section" class="form-field-section row">
                <span class="orders-widgets" class="form-field-widgets col-8">

                </span>
            </div>
            <div class="lab-id-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.labId")}:</span>
                <span class="form-field-widgets col-auto">
                    ${ui.includeFragment("uicommons", "field/text", [
                            id: id + "lab-id-input",
                            label: "",
                            formFieldName: "lab-id",
                            left: true,
                            size: 20,
                            initialValue: ""
                    ])}
                </span>
            </div>
            <div class="specimen-collection-date-section form-field-section row align-items-start">
                <span class="form-field-label col-4">${ui.message("pihapps.specimenCollectionDate")}:</span>
                <span class="form-field-widgets col-auto">
                    ${ui.includeFragment("pihapps", "field/datetimepicker", [
                            id: id+"specimen-date-picker",
                            label: "",
                            formFieldName: "specimen_collection_date",
                            useTime: true,
                            left: true,
                            defaultDate: now
                    ])}
                </span>
                <span class="form-field-widgets col-auto">
                    <input class="specimen-date-estimated" type="checkbox" name="estimated-checkbox" />
                    ${ui.message("pihapps.dateIsEstimated")}
                </span>
            </div>
            <div class="specimen-provider-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.specimenCollectedBy")}:</span>
                <span class="form-field-widgets col-auto">
                    ${ui.includeFragment("pihapps", "field/provider", [
                            id: id + "specimen-provider-picker",
                            initialValue: sessionContext.currentProvider,
                            formFieldName: "specimen_collection_provider",
                    ])}
                </span>
            </div>
            <div class="specimen-location-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.specimenCollectionLocation")}:</span>
                <span class="form-field-widgets col-auto">
                    ${ui.includeFragment("pihapps", "field/location", [
                            id: id + "specimen-location-picker",
                            label: "",
                            valueField: "uuid",
                            initialValue: sessionContext.sessionLocation,
                            formFieldName: "specimen_collection_location",
                            "withTag": "Login Location"
                    ])}
                </span>
            </div>
            <div class="specimen-received-date-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.specimenReceivedDate")}:</span>
                <span class="form-field-widgets col-auto">
                    ${ui.includeFragment("pihapps", "field/datetimepicker", [
                            id: id + "specimen-received-date-picker",
                            label: "",
                            formFieldName: "specimen-received-date",
                            useTime: true,
                            left: true
                    ])}
                </span>
            </div>
            <div class="lab-location-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.labTestLocation")}:</span>
                <span class="form-field-widgets col-auto">
                    ${ui.includeFragment("uicommons", "field/dropDown", [
                            id: id + "test-location-picker",
                            label: "",
                            formFieldName: "test-location-dropdown",
                            left: true,
                            options: [],
                            initialValue: ""
                    ])}
                </span>
            </div>
            <br><br>
            <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
            <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
        </div>
    </form>
</div>