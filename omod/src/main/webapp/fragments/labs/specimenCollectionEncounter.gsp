<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")

    config.require("id")
    def id = config.id
%>

<script type="text/javascript">
    function initializeSpecimenCollectionForm(formConfig) {

        const patientUuid = formConfig.patientUuid;
        const encounter = formConfig.encounter;
        const orders = formConfig.orders;
        const pihAppsConfig = formConfig.pihAppsConfig;
        const onSuccessFunction = formConfig.onSuccessFunction;

        if (!orders || orders.length === 0) {
            return;
        }

        moment.locale(window.sessionContext?.locale ?? 'en');
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);

        const ordersWidgetsSection = parentElement.find(".orders-widgets");
        initializeSelectedOrders({ orders: orders, pihAppsConfig: pihAppsConfig, jqElement: ordersWidgetsSection});

        // Get initial state
        const currentDatetime = dateUtils.roundDownToNearestMinuteInterval(new Date(), 5);
        const initialState = encounter ? {
            patient:  encounter.patient.uuid,
            collectionDate: encounter.encounterDatetime,
            collectionLocation: encounter.location.uuid,
            provider: encounter.encounterProviders && encounter.encounterProviders.length > 0 ? encounter.encounterProviders[0].provider.uuid : null,
            orderNumber: encounter.obs.find(o => o.concept.uuid === pihAppsConfig.labOrderConfig.testOrderNumberQuestion.uuid),
            labId: encounter.obs.find(o => o.concept.uuid === pihAppsConfig.labOrderConfig.labIdentifierConcept.uuid),
            dateEstimated: encounter.obs.find(o => o.concept.uuid === pihAppsConfig.labOrderConfig.estimatedCollectionDateQuestion.uuid),
            receivedDate: encounter.obs.find(o => o.concept.uuid === pihAppsConfig.labOrderConfig.specimenReceivedDateQuestion.uuid),
            testLocation: encounter.obs.find(o => o.concept.uuid === pihAppsConfig.labOrderConfig.testLocationQuestion.uuid)
        } : { };

        // Populate default values each time form is opened
        parentElement.find(".errors-section").html("");
        parentElement.find(":input").val("");

        const estimatedCheckBox = parentElement.find(".specimen-date-estimated");
        estimatedCheckBox.attr("value", pihAppsConfig.labOrderConfig.estimatedCollectionDateAnswer.uuid);
        estimatedCheckBox.removeProp("checked");
        if (initialState.dateEstimated) {
            estimatedCheckBox.prop("checked", true);
        }
        const specimenDate = initialState.collectionDate ? new Date(initialState.collectionDate) : currentDatetime;
        jq(selectorPrefix + "specimen-date-picker-wrapper").datetimepicker("option", "maxDateTime", currentDatetime);
        jq(selectorPrefix + "specimen-date-picker-wrapper").datetimepicker("setDate", specimenDate);
        jq(selectorPrefix + "specimen-location-picker-field").val(initialState.collectionLocation ?? '${sessionContext.sessionLocation.uuid}');

        if (initialState.labId) {
            jq(selectorPrefix + "lab-id-input-field").val(initialState.labId.value);
        }

        if (initialState.receivedDate) {
            jq(selectorPrefix + "specimen-received-date-picker-wrapper").datetimepicker("setDate", new Date(initialState.receivedDate.value));
        }

        const testLocationQuestion = pihAppsConfig.labOrderConfig.testLocationQuestion;
        if (!testLocationQuestion || testLocationQuestion.answers.length === 0) {
            parentElement.find(".lab-location-section").hide();
        }
        else {
            const testLocationField = jq(selectorPrefix + "test-location-picker-field");
            testLocationField.empty();
            testLocationField.append(jq("<option>").attr("value", "").html(""));
            testLocationQuestion.answers?.forEach((answer) => {
                testLocationField.append(jq("<option>").attr("value", answer.uuid).html(answer.display));
            });
            if (initialState.testLocation) {
                testLocationField.val(initialState.testLocation.value.uuid);
            }
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
        formElement.off("submit");
        formElement.on("submit", (event) => {

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
                    parentElement.find(".errors-section").append(jq("<div>").html(e));
                });
                jq(".action-button").removeAttr("disabled");
                return;
            }

            const encounterLocation = getFieldValue(formData, "specimen_collection_location");
            const provider = formData.find(e => e.name === "specimen_collection_provider")?.value;
            const encounterProviders = (provider && encounterRole) ? [{ provider, encounterRole }] : [];

            // If this is a new submission, then process with the encounterFulfillingOrders endpoint
            if (!encounter) {
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
                        encounterDatetime: collectionDateStr,
                        encounterType: pihAppsConfig.labOrderConfig.specimenCollectionEncounterType?.uuid,
                        location: encounterLocation,
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
                        onSuccessFunction();
                    },
                    error: (xhr) => {
                        parentElement.find(".action-button").removeAttr("disabled");
                        const error = xhr?.responseJSON?.error ?? xhr?.responseJSON;
                        const message = error?.translatedMessage ?? error.message ?? error;
                        parentElement.find(".errors-section").html(message);
                    }
                });
            }
            // Otherwise, this is an existing encounter, and allow editing specific fields
            else {
                let updatedEncounter = {
                    uuid: encounter.uuid,
                    patient: encounter.patient.uuid,
                    encounterType: encounter.encounterType.uuid,
                    encounterDatetime: collectionDateStr,
                    location: encounterLocation,
                    obs: []
                }
                if (encounterProviders.length > 0) {
                    updatedEncounter.encounterProviders = encounterProviders;
                }
                const addOrUpdateObs = function(encounterToUpdate, existingObs, newObs) {
                    if (existingObs) {
                        let existingValue = existingObs.value?.uuid ?? existingObs.value;
                        let newValue = newObs.value;
                        const datatype = existingObs.concept.datatype.name;
                        if (datatype === 'Date' || datatype === 'Datetime') {
                            existingValue = dateUtils.formatDateWithTimeIfPresent(existingValue);
                            newValue = dateUtils.formatDateWithTimeIfPresent(newValue);
                        }
                        if (existingValue !== newValue) {
                            updatedEncounter.obs.push({ uuid: existingObs.uuid, voided: true });
                            if (newObs.value) {
                                updatedEncounter.obs.push({ ...newObs, previousVersion: existingObs.uuid });
                            }
                        }
                    }
                    else {
                        if (newObs.value) {
                            updatedEncounter.obs.push(newObs);
                        }
                    }
                }
                addOrUpdateObs(updatedEncounter, initialState.labId, getObs(formData, "lab-id", pihAppsConfig.labOrderConfig.labIdentifierConcept.uuid));
                addOrUpdateObs(updatedEncounter, initialState.dateEstimated, getObs(formData, "estimated-checkbox", pihAppsConfig.labOrderConfig.estimatedCollectionDateQuestion.uuid));
                addOrUpdateObs(updatedEncounter, initialState.receivedDate, getObs(formData, "specimen-received-date", pihAppsConfig.labOrderConfig.specimenReceivedDateQuestion.uuid));
                addOrUpdateObs(updatedEncounter, initialState.testLocation, getObs(formData, "test-location-dropdown", pihAppsConfig.labOrderConfig.testLocationQuestion.uuid));

                jq.ajax({
                    url: openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders/" + encounter.uuid,
                    type: "POST",
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify({encounter: updatedEncounter, orders: orders.map(o => o.uuid)}),
                    dataType: "json",
                    success: () => {
                        onSuccessFunction();
                        parentElement.find(".action-button").removeAttr("disabled");
                    },
                    error: (xhr) => {
                        parentElement.find(".action-button").removeAttr("disabled");
                        const error = xhr?.responseJSON?.error ?? xhr?.responseJSON;
                        const message = error?.translatedMessage ?? error.message ?? error;
                        parentElement.find(".errors-section").html(message);
                    }
                });
            }
        });

        parentElement.show();
    }
</script>

<div id="${id}">
    <div class="errors-section"></div>
    <form>
        <div class="dialog-content form">
            ${ ui.includeFragment("pihapps", "labs/selectedOrders") }
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
                            defaultDate: new Date()
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
                            left: true,
                            clearButton: true
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