<%
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")

    ui.includeJavascript("uicommons", "datetimepicker/bootstrap-datetimepicker.min.js")
    if (org.openmrs.api.context.Context.getLocale().getLanguage() != "en") {
        ui.includeJavascript("uicommons", "datetimepicker/locales/bootstrap-datetimepicker.${ org.openmrs.api.context.Context.getLocale() }.js")
    }
    ui.includeCss("uicommons", "datetimepicker.css")

    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeJavascript("pihapps", "formHelper.js")
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

        const locale = window.sessionContext?.locale ?? 'en';
        const sessionLocationUuid = '${sessionContext.sessionLocation.uuid}';
        moment.locale(locale);
        const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);

        const id = "${id}";
        const selectorPrefix = "#" + id;
        const parentElement = jq(selectorPrefix);

        const ordersWidgetsSection = parentElement.find(".orders-widgets");
        initializeSelectedOrders({ orders: orders, pihAppsConfig: pihAppsConfig, jqElement: ordersWidgetsSection});

        const formName = "pihapps^specimenForm";
        const currentDatetime = dateUtils.roundDownToNearestMinuteInterval(new Date(), 5);

        const formHelper = new FormHelper({
            jq: jq,
            moment: moment,
            locale: locale,
            dateFormat: pihAppsConfig.dateFormat,
            dateTimeFormat: pihAppsConfig.dateTimeFormat,
            formName: formName,
            encounter: encounter,
            patientUuid: patientUuid
        });

        const labIdQuestion = pihAppsConfig.labOrderConfig.labIdentifierConcept;
        const estimatedCollectionDateQuestion = pihAppsConfig.labOrderConfig.estimatedCollectionDateQuestion;
        const estimatedCollectionDateAnswer = pihAppsConfig.labOrderConfig.estimatedCollectionDateAnswer;
        const receivedDateQuestion = pihAppsConfig.labOrderConfig.specimenReceivedDateQuestion;
        const testLocationQuestion = pihAppsConfig.labOrderConfig.testLocationQuestion;

        // Setup form inputs and validation

        // Populate default values each time form is opened
        parentElement.find(".errors-section").html("");
        parentElement.find(":input").val("");

        const labIdWidget = formHelper.createObsWidget(labIdQuestion, { id: id + "-lab-id-input" });
        parentElement.find(".obs-field-lab-id").empty().append(labIdWidget);

        const estimatedDateWidget = formHelper.createObsWidget(estimatedCollectionDateQuestion, {
            id: id + "-estimated-collection-date", valueSet: [ estimatedCollectionDateAnswer ]
        });
        parentElement.find(".obs-field-collection-date-estimated").empty().append(estimatedDateWidget);

        const specimenDateWidget = formHelper.createDatePickerWidget({
            id: id+"-specimen-date-picker",
            useTime: true,
            maxDateTime: currentDatetime,
            initialValue: encounter?.encounterDatetime ?? currentDatetime
        });
        parentElement.find(".encounter-field-encounter-date").empty().append(specimenDateWidget);

        const receivedDateWidget = formHelper.createObsWidget(receivedDateQuestion, {
            id: id + "-received-date", name: "received-date"
        });
        parentElement.find(".obs-field-received-date").empty().append(receivedDateWidget);

        if (testLocationQuestion && testLocationQuestion.answers.length > 0) {
            const testLocationWidget = formHelper.createObsWidget(testLocationQuestion, {
                id: id + "test-location-picker"
            });
            parentElement.find(".obs-field-test-location").empty().append(testLocationWidget);
        }

        jq.get(openmrsContextPath + "/ws/rest/v1/location" + "?tag=Login Location&v=custom:(uuid,display)", function (data) {
            const specimenLocationWidget = formHelper.createSelectWidget({
                id: id + "-specimen-location-picker",
                options: data?.results?.map(r => {
                    return {value: r.uuid, display: r.display };
                }),
                initialValue: encounter?.location?.uuid ?? sessionLocationUuid
            });
            parentElement.find(".obs-field-specimen-collection-location").empty().append(specimenLocationWidget);
        });

        const encounterRole = pihAppsConfig.labOrderConfig.specimenCollectionEncounterRole?.uuid;
        // TODO: Handle specimen collection provider with appropriate role.  Not yet implemented.

        const saveButton = parentElement.find(".action-button.confirm");
        saveButton.off("click");
        saveButton.on("click", (event) => {

            event.preventDefault();
            parentElement.find(".action-button").attr("disabled", "disabled");
            parentElement.find(".errors-section").html("");

            const encounterToSubmit = formHelper.constructEncounterPayload();
            if (!encounterToSubmit.encounterType) {
                encounterToSubmit.encounterType = pihAppsConfig.labOrderConfig.specimenCollectionEncounterType.uuid;
            }
            encounterToSubmit.encounterDatetime = jq("#" + id+"-specimen-date-picker-field").val();
            encounterToSubmit.location = jq("#" + id+"-specimen-location-picker").val();

            const errors = [];
            const currentDate = moment();
            const collectionDateStr =  encounterToSubmit.encounterDatetime;
            const collectionDate = collectionDateStr ? moment(collectionDateStr) : null;
            if (collectionDate && collectionDate.isAfter(currentDate)) {
                errors.push('${ ui.message("pihapps.specimenCollectionDateCannotBeFuture") }');
            }
            const receivedDateStr = encounterToSubmit.obs.find(o => o.concept === receivedDateQuestion.uuid)?.valueDatetime;
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

            // If this is a new submission, then add in order numbers as obs
            if (!encounterToSubmit.uuid) {
                orders.forEach((o, index) => {
                    encounterToSubmit.obs.push({
                        concept: pihAppsConfig.labOrderConfig.testOrderNumberQuestion.uuid,
                        valueText: o.orderNumber,
                        formNamespaceAndPath: formName + "/order_number_" + index
                    });
                });
            }

            const payload = { encounter: encounterToSubmit, orders: orders.map(o => o.uuid) };
            jq.ajax({
                url: openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders",
                type: "POST",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify(payload),
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
                    <span class="obs-field-lab-id"></span>
                </span>
            </div>
            <div class="specimen-collection-date-section form-field-section row align-items-start">
                <span class="form-field-label col-4">${ui.message("pihapps.specimenCollectionDate")}:</span>
                <span class="form-field-widgets col-auto">
                    <span class="encounter-field-encounter-date"></span>
                </span>
                <span class="form-field-widgets col" style="margin-top: 10px;">
                    <span class="obs-field-collection-date-estimated"></span>
                    <span class="form-field-label>">${ui.message("pihapps.dateIsEstimated")}</span>
                </span>
            </div>
            <div class="specimen-location-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.specimenCollectionLocation")}:</span>
                <span class="form-field-widgets col-auto">
                    <span class="obs-field-specimen-collection-location"></span>
                </span>
            </div>
            <div class="specimen-received-date-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.specimenReceivedDate")}:</span>
                <span class="form-field-widgets col-auto">
                    <span class="obs-field-received-date"></span>
                </span>
            </div>
            <div class="lab-location-section form-field-section row">
                <span class="form-field-label col-4">${ui.message("pihapps.labTestLocation")}:</span>
                <span class="form-field-widgets col-auto">
                    <span class="obs-field-test-location"></span>
                </span>
            </div>
            <br><br>
            <button class="cancel action-button">${ ui.message("coreapps.cancel") }</button>
            <button class="confirm right action-button">${ ui.message("coreapps.save") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
        </div>
    </form>
</div>