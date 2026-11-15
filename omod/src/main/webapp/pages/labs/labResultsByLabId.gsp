<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("uicommons", "datetimepicker/bootstrap-datetimepicker.min.js")
    if (org.openmrs.api.context.Context.getLocale().getLanguage() != "en") {
        ui.includeJavascript("uicommons", "datetimepicker/locales/bootstrap-datetimepicker.${ org.openmrs.api.context.Context.getLocale() }.js")
    }
    ui.includeCss("uicommons", "datetimepicker.css")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "formHelper.js")
    ui.includeJavascript("pihapps", "labs/labResultFields.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def validateMode = (mode == "validate")
    def title = ui.message(validateMode ? "pihapps.labResultsByLabId.validateResults" : "pihapps.labResultsByLabId.enterResults")
    def labOrderListLink = ui.pageLink("pihapps", "labs/labOrderList") + "?grouping=patient"
    def patientIdentifier = patient.patientIdentifier ? patient.patientIdentifier.identifier : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labOrderList")) }", link: '${ ui.encodeJavaScript(labOrderListLink) }' },
        { label: "${ ui.encodeJavaScript(title) }" }
    ];
</script>

<style>
    .lr-page { max-width: 1200px; }
    .lr-title { display: flex; flex-wrap: wrap; align-items: baseline; gap: 16px; margin-bottom: 4px; }
    .lr-title h2 { margin: 0; }
    .lr-patient { color: #455a64; font-size: 1.05em; }
    .lr-hint { color: #607d8b; font-size: 0.9em; margin: 4px 0 12px; }

    /* Choice between the Lab IDs of the patient */
    .lr-groups { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0 16px; }
    .lr-group-choice { border: 1px solid #cfd8dc; border-radius: 6px; padding: 6px 10px; background: #fff; color: #37474f;
                       text-decoration: none; max-width: 380px; line-height: 1.3; }
    .lr-group-choice:hover { border-color: #00473f; text-decoration: none; }
    .lr-group-choice.lr-current { border-color: #00473f; box-shadow: inset 0 0 0 1px #00473f; background: #f1f8f6; }
    .lr-group-choice small { display: block; color: #607d8b; }

    .lr-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 12px; }
    .lr-toolbar .lr-label { color: #455a64; font-weight: bold; }

    .lr-specimen { border: 1px solid #d9e2e7; border-radius: 6px; background: #fff; margin-bottom: 16px; }
    .lr-specimen.lr-saved { opacity: 0.6; }
    .lr-specimen-header { display: flex; flex-wrap: wrap; gap: 24px; padding: 8px 12px; background: #f5f7f8;
                          border-bottom: 1px solid #d9e2e7; border-radius: 6px 6px 0 0; }
    .lr-specimen-header .lr-label { color: #607d8b; }
    .lr-category { padding: 4px 12px 8px; }
    .lr-category + .lr-category { border-top: 1px solid #d9e2e7; }
    .lr-category-header { display: flex; align-items: center; justify-content: space-between; margin: 8px 0 2px; }
    .lr-category-header h4 { margin: 0; font-size: 1em; color: #00473f; text-transform: capitalize; }
    .lr-category-header button { font-size: 0.85em; padding: 2px 8px; }

    /* Every line of the table: test, result, normal range, status (or who entered the results) */
    .lr-row, .lr-specimen .result-row {
        display: grid; grid-template-columns: 200px 310px 220px minmax(0, 1fr); column-gap: 12px; align-items: center;
        margin: 0; padding: 3px 0; min-height: 32px;
    }
    .lr-specimen .result-row > span { flex: none; max-width: none; width: auto; padding: 0; }
    .lr-columns { color: #78909c; font-size: 0.8em; text-transform: uppercase; border-bottom: 1px solid #eceff1; min-height: 0; }
    .lr-order { border-bottom: 1px solid #f1f3f4; }
    .lr-order:last-child { border-bottom: none; }
    .lr-order .test-name, .lr-name { font-weight: bold; }
    .lr-panel-members .test-name { font-weight: normal; padding-left: 16px; }
    .lr-panel-members .lr-subpanel-name { color: #37474f; font-weight: bold; padding-left: 16px; }
    .lr-panel-members .lr-subpanel .test-name { padding-left: 32px; }
    .lr-panel-members .result-row { min-height: 28px; padding: 1px 0; }
    .lr-status-cell { justify-self: end; }
    .lr-status-cell .btn { padding: 1px 8px; font-size: 0.85em; }
    .lr-not-performed .result-row .form-field-widgets { visibility: hidden; }
    .lr-not-performed .lr-panel-members { display: none; }
    .lr-reason { margin: 0 0 6px 212px; }
    .lr-note { color: #b26a00; font-size: 0.85em; margin: 0 0 4px 212px; }
    .lr-note-top { color: #b26a00; margin: 0 0 12px; }
    .lr-hidden { display: none; }

    /* Result fields: the same width for each kind of field, so that units and comments line up */
    .lr-specimen .obs-widget { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 0 8px; }
    .lr-specimen input.result-numeric-input { width: 110px; min-width: 0; }
    .lr-specimen input.result-text-input, .lr-specimen select.result-value-field { width: 210px; min-width: 0; }
    .lr-specimen .result-units { display: inline-block; width: 90px; padding-left: 0; color: #546e7a; font-size: 0.9em; }
    .lr-specimen .comment-toggle { font-size: 0.8em; color: #90a4ae; }
    .lr-specimen .comment-section { flex-basis: 100%; }
    .lr-specimen .comment-section textarea { width: 330px; margin-top: 2px; }
    .lr-specimen .multi-value-add { font-size: 0.8em; padding: 0 6px; }
    .lr-specimen .reference-range, .lr-range { color: #546e7a; font-size: 0.9em; }
    .lr-specimen .form-field-widgets p { margin: 0; }

    /* Validation */
    .lr-value.lr-abnormal { color: #e65100; font-weight: bold; }
    .lr-value.lr-critical { color: #c62828; font-weight: bold; }
    .lr-entered-by { color: #607d8b; font-size: 0.85em; }
    .lr-validate .lr-row { grid-template-columns: 200px 250px 220px 150px minmax(0, 1fr); }
    .lr-validate .lr-entered-by { color: #607d8b; font-size: 0.85em; }
    .lr-cannot-decide { color: #b26a00; font-size: 0.85em; text-align: right; display: inline-block; max-width: 260px; }
    .lr-return-reason { margin: 2px 0 8px 212px; }
    .lr-return-reason label { display: block; color: #455a64; font-size: 0.9em; }
    .lr-return-reason textarea { width: 480px; max-width: 100%; }
    .lr-reason-required { display: block; color: #c62828; font-size: 0.85em; font-weight: bold; }
    .lr-decision-summary { color: #455a64; align-self: center; }
    .lr-order.lr-saved { opacity: 0.6; }

    .lr-errors { color: #c62828; margin: 8px 0; }
    .lr-reminder { border: 1px solid #ffb74d; background: #fff8e1; border-radius: 6px; padding: 10px 12px; margin: 8px 0; }
    .lr-reminder p { margin: 0 0 8px; }
    .lr-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: flex-start; margin: 12px 0; }
    .lr-actions textarea { min-width: 320px; }
    .lr-empty { color: #607d8b; padding: 16px 0; }
</style>

<div class="lr-page">
    <div class="lr-title">
        <h2>${ ui.escapeHtml(title) }</h2>
        <span class="lr-patient">${ ui.escapeHtml(patientIdentifier) } — ${ ui.escapeHtml(patient.personName ? patient.personName.fullName : "") }</span>
    </div>

<% if (!authorized) { %>
    <div class="lr-empty">${ ui.message(validateMode ? "pihapps.labResultsByLabId.notAuthorizedToValidate" : "pihapps.labResultsByLabId.notAuthorizedToEnter") }</div>
    <a class="button" href="${ labOrderListLink }">${ ui.message("pihapps.labResultsByLabId.backToLabOrders") }</a>
<% } else { %>
    <div id="lr-groups" class="lr-groups"></div>
    <div id="lr-loading"><i class="icon-spinner icon-spin icon-2x"></i></div>
    <div id="lr-content"></div>
<% } %>
</div>

<% if (authorized) { %>
<script type="text/javascript">
    jq(function() {
        const validateMode = ${ validateMode };
        const patientUuid = '${ patient.uuid }';
        const requestedLabId = '${ ui.encodeJavaScript(labId ?: "") }';
        const labOrderListLink = '${ ui.encodeJavaScript(labOrderListLink) }';

        // Results recorded as obs groups that an implementation's own editor enters (see labResultFields.js), and
        // that its renderer shows (as on the patient lab results page), by concept uuid
        const resultEditors = PihAppsLabResultFields.getResultEditors(${ ui.toJson(resultEditorExtensions) });
        const resultRenderers = PihAppsLabResultFields.getResultRenderers(${ ui.toJson(resultRendererExtensions) });
        const locale = window.sessionContext?.locale ?? 'en';
        moment.locale(locale);

        const messages = {
            estimated: '${ ui.encodeJavaScript(ui.message("pihapps.estimated")) }',
            valueMustBeInteger: '${ ui.encodeJavaScript(ui.message("pihapps.valueCannotBeDecimal")) }',
            abnormalValue: '${ ui.encodeJavaScript(ui.message("pihapps.abnormalValue")) }',
            criticalValue: '${ ui.encodeJavaScript(ui.message("pihapps.criticalValue")) }',
            minimumAllowedValue: '${ ui.encodeJavaScript(ui.message("pihapps.minimumAllowedValue")) }',
            maximumAllowedValue: '${ ui.encodeJavaScript(ui.message("pihapps.maximumAllowedValue")) }',
            resultDateCannotBeFuture: '${ ui.encodeJavaScript(ui.message("pihapps.resultDateCannotBeFuture")) }',
            resultDateCannotBeBeforeSpecimenDate: '${ ui.encodeJavaScript(ui.message("pihapps.resultDateCannotBeBeforeSpecimenDate")) }',
            errorsWithOneOrMoreFields: '${ ui.encodeJavaScript(ui.message("pihapps.errorsWithOneOrMoreFields")) }',
            reasonRequired: '${ ui.encodeJavaScript(ui.message("pihapps.reasonRequired")) }',
            addComment: '${ ui.encodeJavaScript(ui.message("pihapps.addComment")) }',
            removeComment: '${ ui.encodeJavaScript(ui.message("pihapps.removeComment")) }',
            removeResult: '${ ui.encodeJavaScript(ui.message("pihapps.removeResult")) }',
            addAnotherResult: '${ ui.encodeJavaScript(ui.message("pihapps.addAnotherResult")) }',
            submitForValidation: '${ ui.encodeJavaScript(ui.message("pihapps.submitForValidation")) }',
            returnedForCorrection: '${ ui.encodeJavaScript(ui.message("pihapps.orderFulfillmentEventType.RESULTS_RETURNED")) }',
            labId: '${ ui.encodeJavaScript(ui.message("pihapps.labId")) }',
            specimenCollectionDate: '${ ui.encodeJavaScript(ui.message("pihapps.specimenCollectionDate")) }',
            specimenReceivedDate: '${ ui.encodeJavaScript(ui.message("pihapps.specimenReceivedDate")) }',
            resultDate: '${ ui.encodeJavaScript(ui.message("pihapps.resultDate")) }',
            reason: '${ ui.encodeJavaScript(ui.message("pihapps.reason")) }',
            labTest: '${ ui.encodeJavaScript(ui.message("pihapps.labTest")) }',
            result: '${ ui.encodeJavaScript(ui.message("pihapps.result")) }',
            normalRange: '${ ui.encodeJavaScript(ui.message("pihapps.normalRange")) }',
            status: '${ ui.encodeJavaScript(ui.message("pihapps.status")) }',
            save: '${ ui.encodeJavaScript(ui.message("coreapps.save")) }',
            backToLabOrders: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.backToLabOrders")) }',
            otherCategory: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.otherCategory")) }',
            noResultsAwaitingValidation: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.noResultsAwaitingValidation")) }',
            noTestsAwaitingResults: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.noTestsAwaitingResults")) }',
            validationHint: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.validationHint")) }',
            markAll: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.markAll")) }',
            unfinishedResults: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.unfinishedResults")) }',
            changeStatusAndSave: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.changeStatusAndSave")) }',
            saveWithoutChangingStatus: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.saveWithoutChangingStatus")) }',
            resultsSaved: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.resultsSaved")) }',
            alreadySaved: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.alreadySaved")) }',
            unableToSave: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.unableToSave")) }',
            unableToLoad: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.unableToLoad")) }',
            unableToLoadResultEntry: '${ ui.encodeJavaScript(ui.message("pihapps.unableToLoadResultEntry")) }',
            enteredBy: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.enteredBy")) }',
            enteredByCurrentUser: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.enteredByCurrentUser")) }',
            decision: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.decision")) }',
            decisionValidate: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.decisionValidate")) }',
            decisionReturn: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.decisionReturn")) }',
            returnReason: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.returnReason")) }',
            decisionSummary: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.decisionSummary")) }',
            decisionsSaved: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.decisionsSaved")) }',
            alreadyRecorded: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.alreadyRecorded")) }',
            validated: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.validated")) }',
            nothingToSave: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.nothingToSave")) }',
            moreOrdersThanShown: '${ ui.encodeJavaScript(ui.message("pihapps.labResultsByLabId.moreOrdersThanShown")) }',
        };
        // Replaces {0}, {1}... in a message
        const format = (message, ...args) => message.replace(/[{]([0-9]+)[}]/g, (match, i) => args[i] ?? match);

        const rest = openmrsContextPath + "/ws/rest/v1/";
        const getJson = (url) => new Promise((resolve, reject) => jq.getJSON(url).done(resolve).fail(reject));
        const postJson = (url, payload) => new Promise((resolve, reject) => jq.ajax({
            url: url, type: "POST", contentType: "application/json; charset=utf-8", data: JSON.stringify(payload), dataType: "json"
        }).done(resolve).fail(reject));
        const errorMessage = (xhr) => {
            const error = xhr?.responseJSON?.error ?? xhr?.responseJSON;
            return error?.translatedMessage ?? error?.message ?? messages.unableToSave;
        };
        const escapeHtml = (text) => jq("<span>").text(text ?? "").html();

        // Leaving the page with unsaved results asks first
        let dirty = false;
        window.addEventListener("beforeunload", (event) => {
            if (dirty) {
                event.preventDefault();
                event.returnValue = "";
            }
        });

        const configRep = "dateFormat,dateTimeFormat,labOrderConfig:(labTestOrderType:(uuid)," +
            "availableLabTestsByCategory:(category:(uuid,displayStringForLab),labTests:(uuid)),fulfillerStatusOptions:(status,display)," +
            "labIdentifierConcept:(uuid),specimenReceivedDateQuestion:(uuid),resultsDateQuestion:(uuid,datatype:(name))," +
            "reasonTestNotPerformedQuestion:(uuid,datatype:(name),answers:(uuid,display)),collectResultComments,requireResultValidation)";
        const orderRep = "uuid,orderNumber,dateActivated,dateStopped,fulfillerStatus,labResultValidation,accessionNumber,urgency," +
            "patient:(uuid),encounter:(uuid),fulfillerEncounter:(uuid),concept:(uuid,displayStringForLab)";
        const obsRep = "uuid,concept:(uuid,datatype:(name)),value,valueCoded:(uuid,display),valueDatetime,valueText,valueNumeric,comment,formNamespaceAndPath,order:(uuid)";
        const encounterRep = "uuid,encounterDatetime,encounterType:(uuid),location:(uuid,display)," +
            "encounterProviders:(provider:(uuid,display),encounterRole:(uuid,display)),obs:(" + obsRep + ",groupMembers:(" + obsRep + ",groupMembers:(" + obsRep + ")))";

        getJson(rest + "pihapps/config?v=custom:(" + configRep + ")").then(config => {
            const labConfig = config.labOrderConfig;
            const requireResultValidation = labConfig.requireResultValidation;
            const dateUtils = new PihAppsDateUtils(moment, config.dateFormat, config.dateTimeFormat);

            // Status labels as on the single order results form: completing results submits them for validation
            const statusLabel = (status) => (requireResultValidation && status === 'COMPLETED')
                ? messages.submitForValidation
                : (labConfig.fulfillerStatusOptions.find(o => o.status === status)?.display ?? status);
            const validationEventType = (order) => (requireResultValidation && order.fulfillerStatus === 'IN_PROGRESS')
                ? order.labResultValidation?.eventType : null;
            const isAwaitingValidation = (order) => validationEventType(order) === 'RESULTS_SUBMITTED';

            // The category of each test, so that each specimen shows a section per category
            const categoryOfTest = {};
            const categoryOrder = [];
            labConfig.availableLabTestsByCategory.forEach(c => {
                categoryOrder.push(c.category.uuid);
                c.labTests.forEach(t => { categoryOfTest[t.uuid] = c.category; });
            });
            const otherCategory = { uuid: "other", displayStringForLab: messages.otherCategory };

            const statuses = validateMode ? ["AWAITING_VALIDATION"]
                : (requireResultValidation ? ["IN_FULFILLMENT", "AWAITING_VALIDATION"] : ["IN_FULFILLMENT"]);
            const maxOrders = 100;
            const orderQuery = jq.param({
                patient: patientUuid,
                orderType: labConfig.labTestOrderType?.uuid,
                orderFulfillmentStatus: statuses,
                limit: maxOrders,
                v: "custom:(" + orderRep + ")"
            }, true);

            return getJson(rest + "pihapps/labOrder?" + orderQuery).then(data => {
                const orders = (data.results || []).filter(o => o.fulfillerEncounter && !o.dateStopped)
                    .filter(o => !validateMode || isAwaitingValidation(o));
                if ((data.links || []).some(link => link.rel === "next")) {
                    jq("#lr-content").append(jq("<div>").addClass("lr-note-top").text(format(messages.moreOrdersThanShown, maxOrders)));
                }
                if (orders.length === 0) {
                    jq("#lr-loading").hide();
                    jq("#lr-content").append(jq("<div>").addClass("lr-empty")
                        .text(validateMode ? messages.noResultsAwaitingValidation : messages.noTestsAwaitingResults))
                        .append(jq("<a>").addClass("button").attr("href", labOrderListLink).text(messages.backToLabOrders));
                    return;
                }

                // The orders are grouped by Lab ID, STAT first and then the oldest, and the page shows one Lab ID: the
                // one requested, or the first. Results are saved in the specimen collection encounter, so a Lab ID
                // recorded for two collections shows each of them. Orders without a Lab ID are grouped by their
                // specimen collection encounter.
                const labIdGroups = [];
                orders.forEach(order => {
                    const key = order.accessionNumber?.trim() || ("encounter:" + order.fulfillerEncounter.uuid);
                    let labIdGroup = labIdGroups.find(g => g.key === key);
                    if (!labIdGroup) {
                        labIdGroup = { key: key, orders: [], specimens: [] };
                        labIdGroups.push(labIdGroup);
                    }
                    labIdGroup.orders.push(order);
                    let specimen = labIdGroup.specimens.find(s => s.encounterUuid === order.fulfillerEncounter.uuid);
                    if (!specimen) {
                        specimen = { encounterUuid: order.fulfillerEncounter.uuid, orders: [] };
                        labIdGroup.specimens.push(specimen);
                    }
                    specimen.orders.push(order);
                });
                labIdGroups.forEach(g => {
                    g.orderedOn = g.orders.map(o => o.dateActivated).sort()[0];
                    g.stat = g.orders.some(o => o.urgency === 'STAT');
                });
                labIdGroups.sort((a, b) => (b.stat - a.stat) || (a.orderedOn < b.orderedOn ? -1 : a.orderedOn > b.orderedOn ? 1 : 0));
                const group = labIdGroups.find(g => g.key === requestedLabId) ?? labIdGroups[0];
                renderLabIdChoice(labIdGroups, group);

                const conceptUuids = [...new Set(group.orders.map(o => o.concept.uuid))];
                const orderables = {};
                const loads = conceptUuids.map(uuid => getJson(rest + "concept/" + uuid + "?v=custom:(" + PihAppsLabResultFields.orderableRep + ")")
                    .then(concept => { orderables[uuid] = concept; }));
                group.specimens.forEach(specimen => {
                    loads.push(getJson(rest + "encounter/" + specimen.encounterUuid + "?v=custom:(" + encounterRep + ")")
                        .then(encounter => { specimen.encounter = encounter; }));
                });
                let validation = {};
                if (validateMode) {
                    loads.push(getJson(rest + "pihapps/labResultValidation?" + jq.param({ order: group.orders.map(o => o.uuid) }, true))
                        .then(v => { validation = v; }));
                }

                return Promise.all(loads).then(() => {
                    jq("#lr-loading").hide();
                    jq("#lr-content").append(validateMode
                        ? renderValidation(group, orderables, validation)
                        : renderEntry(group, orderables));
                });
            });

            // ---- Shared ----

            function renderLabIdChoice(labIdGroups, current) {
                if (labIdGroups.length < 2) {
                    return;
                }
                labIdGroups.forEach(g => {
                    const tests = g.orders.map(o => o.concept.displayStringForLab);
                    const href = "?" + jq.param({ patient: patientUuid, mode: validateMode ? "validate" : "enter", labId: g.key });
                    const choice = jq("<a>").addClass("lr-group-choice").attr("href", href)
                        .toggleClass("lr-current", g === current)
                        .append(jq("<strong>").html((g.stat ? '<i class="fas fa-fw fa-exclamation" style="color:red;"></i>' : '')
                            + escapeHtml(messages.labId + " " + (g.orders[0].accessionNumber?.trim() || "-"))))
                        .append(jq("<small>").text(dateUtils.formatAsDateWithoutTime(g.orderedOn) + " · "
                            + tests.slice(0, 4).join(", ") + (tests.length > 4 ? " +" + (tests.length - 4) : "")));
                    jq("#lr-groups").append(choice);
                });
            }

            function groupByCategory(orders) {
                const groups = {};
                orders.forEach(order => {
                    const category = categoryOfTest[order.concept.uuid] ?? otherCategory;
                    (groups[category.uuid] = groups[category.uuid] || { category: category, orders: [] }).orders.push(order);
                });
                return Object.values(groups).sort((a, b) => {
                    const ai = categoryOrder.indexOf(a.category.uuid), bi = categoryOrder.indexOf(b.category.uuid);
                    return (ai < 0 ? 9999 : ai) - (bi < 0 ? 9999 : bi);
                });
            }

            function specimenHeader(specimen, obsValue) {
                const labId = specimen.orders[0].accessionNumber ?? obsValue(labConfig.labIdentifierConcept?.uuid)?.valueText ?? "";
                const received = obsValue(labConfig.specimenReceivedDateQuestion?.uuid)?.valueDatetime;
                const item = (label, value) => jq("<span>").append(jq("<span>").addClass("lr-label").text(label + ": ")).append(jq("<strong>").text(value ?? ""));
                return jq("<div>").addClass("lr-specimen-header")
                    .append(item(messages.labId, labId))
                    .append(item(messages.specimenCollectionDate, dateUtils.formatDateWithTimeIfPresent(specimen.encounter.encounterDatetime)))
                    .append(received ? item(messages.specimenReceivedDate, dateUtils.formatDateWithTimeIfPresent(received)) : null);
            }

            function testName(order) {
                return (order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color:red;"></i>' : '')
                    + escapeHtml(order.concept.displayStringForLab);
            }

            function columnsRow(lastColumn) {
                return jq("<div>").addClass("lr-row lr-columns")
                    .append(jq("<span>").text(messages.labTest))
                    .append(jq("<span>").text(messages.result))
                    .append(jq("<span>").text(messages.normalRange))
                    .append(jq("<span>").addClass("lr-status-cell").text(lastColumn));
            }

            function showErrors(container, errors) {
                container.find(".lr-errors").first().empty();
                errors.forEach(e => container.find(".lr-errors").first().append(jq("<div>").text(e)));
            }

            // ---- Entering results: every test of the Lab ID on one form, saved together ----

            function renderEntry(group, orderables) {
                const page = jq("<div>");
                if (requireResultValidation) {
                    page.append(jq("<div>").addClass("lr-hint").text(messages.validationHint));
                }

                // One result date, recorded on each test that is completed. It defaults to today.
                const resultDateQuestion = labConfig.resultsDateQuestion;
                const resultDatePicker = jq("<span>");
                const toolbar = jq("<div>").addClass("lr-toolbar")
                    .append(jq("<span>").addClass("lr-label").text(messages.resultDate))
                    .append(resultDatePicker);
                page.append(toolbar);

                const blocks = [];
                const specimenForms = [];
                let existingResultDate = null;
                group.specimens.forEach((specimen, specimenIndex) => {
                    const encounter = specimen.encounter;
                    const formHelper = new FormHelper({
                        jq: jq, moment: moment, locale: locale,
                        dateFormat: config.dateFormat, dateTimeFormat: config.dateTimeFormat,
                        formName: "pihapps^labResultForm", encounter: encounter, patientUuid: patientUuid
                    });
                    const obsFor = (conceptUuid, order) => formHelper.initialObs.find(o => o.concept.uuid === conceptUuid && o.order?.uuid === order.uuid);
                    const specimenBlocks = [];
                    const card = jq("<div>").addClass("lr-specimen");
                    card.append(specimenHeader(specimen, (conceptUuid) => formHelper.getInitialObsValue(conceptUuid)));

                    groupByCategory(specimen.orders).forEach(categoryGroup => {
                        const section = jq("<div>").addClass("lr-category");
                        // Marking all only when there is more than one test: otherwise the test's own status does the same
                        const markAllCompleted = jq("<button>").attr({ type: "button", tabindex: -1 }).text(format(messages.markAll, statusLabel("COMPLETED")));
                        section.append(jq("<div>").addClass("lr-category-header")
                            .append(jq("<h4>").text(categoryGroup.category.displayStringForLab.toLowerCase()))
                            .append(categoryGroup.orders.length > 1 ? markAllCompleted : null));
                        section.append(columnsRow(messages.status));

                        const categoryBlocks = [];
                        categoryGroup.orders.forEach(order => {
                            existingResultDate = existingResultDate ?? obsFor(resultDateQuestion.uuid, order)?.valueDatetime;
                            const block = renderEntryOrder(order, orderables[order.concept.uuid], formHelper, obsFor, "lr-" + specimenIndex + "-" + order.uuid);
                            section.append(block.element);
                            categoryBlocks.push(block);
                        });
                        markAllCompleted.on("click", () => categoryBlocks.forEach(b => b.setStatus("COMPLETED")));
                        specimenBlocks.push(...categoryBlocks);
                        card.append(section);
                    });
                    blocks.push(...specimenBlocks);
                    specimenForms.push({ specimen: specimen, formHelper: formHelper, blocks: specimenBlocks, card: card, saved: false });
                    page.append(card);
                });

                const datePicker = specimenForms[0].formHelper.createDatePickerWidget({
                    id: "lr-result-date",
                    locale: locale,
                    useTime: resultDateQuestion.datatype.name === 'Datetime',
                    initialValue: existingResultDate ?? moment().startOf('day').format()
                });
                datePicker.find(".add-on, .icon-remove").attr("tabindex", -1);
                resultDatePicker.append(datePicker);

                const errors = jq("<div>").addClass("lr-errors");
                const reminder = jq("<div>").addClass("lr-reminder lr-hidden");
                const saveButton = jq("<button>").addClass("confirm").text(messages.save);
                const backLink = jq("<a>").addClass("button").attr("href", labOrderListLink).text(messages.backToLabOrders);
                page.append(errors).append(reminder).append(jq("<div>").addClass("lr-actions").append(saveButton).append(backLink));

                page.on("change input", ":input", () => { dirty = true; });

                const save = (markCompleted) => {
                    reminder.addClass("lr-hidden").empty();
                    markCompleted.forEach(b => b.setStatus("COMPLETED"));
                    const resultDate = datePicker.find("#lr-result-date-field").val();
                    const problems = [];
                    if (resultDate && moment(resultDate).isAfter(moment())) {
                        problems.push(messages.resultDateCannotBeFuture);
                    }
                    // Only the tests the user changed are saved: the others, eg on hold or already submitted, are left as they are
                    const changedForms = specimenForms.filter(form => !form.saved)
                        .map(form => Object.assign({}, form, { changedBlocks: form.blocks.filter(b => b.isChanged()) }))
                        .filter(form => form.changedBlocks.length > 0);
                    if (changedForms.length === 0) {
                        showErrors(page, [messages.nothingToSave]);
                        return;
                    }
                    changedForms.forEach(form => {
                        // Compare calendar dates only, as the single order results form does
                        if (resultDate && form.specimen.encounter.encounterDatetime.substring(0, 10) > resultDate.substring(0, 10)
                            && form.changedBlocks.some(b => b.status() === 'COMPLETED')) {
                            problems.push(messages.resultDateCannotBeBeforeSpecimenDate);
                        }
                    });
                    changedForms.flatMap(form => form.changedBlocks).forEach(b => {
                        if (b.status() === 'EXCEPTION' && !b.reasonValue()) {
                            problems.push(b.order.concept.displayStringForLab + ": " + messages.reasonRequired);
                        }
                    });
                    if (page.find(".field-error.error-value").filter((i, e) => jq(e).text().trim()).length > 0) {
                        problems.push(messages.errorsWithOneOrMoreFields);
                    }
                    showErrors(page, [...new Set(problems)]);
                    if (problems.length > 0) {
                        return;
                    }

                    changedForms.forEach(form => form.changedBlocks.forEach(b => b.prepareForSave(resultDate)));
                    saveButton.attr("disabled", "disabled");
                    // Each specimen is saved with its own encounter, in one request with its editor results, one
                    // after the other. If one fails, those already saved are shown as saved and left out when saving
                    // again, as their form no longer matches what is saved.
                    changedForms.reduce((previous, form) => previous.then(() => {
                        const payload = {
                            encounter: form.formHelper.constructEncounterPayload(),
                            orders: form.changedBlocks.map(b => ({ uuid: b.order.uuid, fulfillerStatus: b.status() })),
                            editorResults: PihAppsLabResultFields.getEditorResults(form.changedBlocks.flatMap(b => b.editors))
                        };
                        return postJson(rest + "encounterFulfillingOrders/" + form.specimen.encounterUuid, payload)
                            .then(() => {
                                const original = specimenForms.find(f => f.specimen === form.specimen);
                                original.saved = true;
                                original.card.addClass("lr-saved").find(":input").prop("disabled", true);
                            });
                    }), Promise.resolve()).then(() => {
                        dirty = false;
                        emr.successMessage(messages.resultsSaved);
                        window.location.reload();
                    }).catch(xhr => {
                        saveButton.removeAttr("disabled");
                        const saved = [...new Set(specimenForms.filter(form => form.saved).map(form => form.specimen.orders[0].accessionNumber))];
                        showErrors(page, [errorMessage(xhr)].concat(saved.length > 0 ? [format(messages.alreadySaved, saved.join(", "))] : []));
                    });
                };

                saveButton.on("click", () => {
                    // Tests with results that are still in progress are probably meant to be completed: ask
                    const unfinished = blocks.filter(b => b.status() === 'IN_PROGRESS' && b.hasResults());
                    if (unfinished.length === 0) {
                        save([]);
                        return;
                    }
                    const markAndSave = jq("<button>").addClass("confirm").text(format(messages.changeStatusAndSave, statusLabel("COMPLETED")));
                    const saveInProgress = jq("<button>").text(format(messages.saveWithoutChangingStatus, statusLabel("IN_PROGRESS")));
                    reminder.empty().removeClass("lr-hidden")
                        .append(jq("<p>").text(format(messages.unfinishedResults, statusLabel("IN_PROGRESS"),
                            unfinished.map(b => b.order.concept.displayStringForLab).join(", "), statusLabel("COMPLETED"))))
                        .append(markAndSave).append(" ").append(saveInProgress);
                    markAndSave.on("click", () => save(unfinished));
                    saveInProgress.on("click", () => save([]));
                    markAndSave.focus();
                });
                return page;
            }

            function renderEntryOrder(order, orderable, formHelper, obsFor, idPrefix) {
                const element = jq("<div>").addClass("lr-order");
                const isPanel = PihAppsLabResultFields.isPanel(orderable, resultEditors);
                let changed = false;  // whether the user changed anything of the test: only changed tests are saved

                // Results awaiting validation open as completed, without "In progress": the server can't tell that
                // choice from re-saving the specimen, so the order would stay awaiting validation.  Results returned
                // for correction open as completed too, as on the single order form, as they are usually resubmitted
                const awaitingValidation = isAwaitingValidation(order);
                const returnedForCorrection = validationEventType(order) === 'RESULTS_RETURNED';
                const initialStatus = (awaitingValidation || returnedForCorrection) ? 'COMPLETED' : 'IN_PROGRESS';
                const statusOptions = (awaitingValidation ? ["COMPLETED", "EXCEPTION"] : ["IN_PROGRESS", "COMPLETED", "EXCEPTION"])
                    .map(s => ({ value: s, display: statusLabel(s) }));
                const statusWidget = formHelper.createSelectWidget({
                    id: idPrefix + "-status", options: statusOptions, initialValue: initialStatus, includeEmptyOption: false
                });
                const statusCell = jq("<span>").addClass("lr-status-cell").append(statusWidget);
                formHelper.displaySelectWidgetAsButtons(statusWidget, { buttonClass: "btn-outline-secondary btn-sm" });
                statusCell.find(".select-buttons button").each((i, button) => {
                    jq(button).attr({ type: "button", tabindex: -1, "data-value": statusOptions[i].value });
                });

                const results = jq("<div>").addClass("lr-results");
                const editors = [];
                PihAppsLabResultFields.render({
                    jq: jq,
                    formHelper: formHelper,
                    order: order,
                    orderable: orderable,
                    container: results,
                    idPrefix: idPrefix + "-",
                    collectResultComments: labConfig.collectResultComments,
                    messages: messages,
                    resultEditors: resultEditors,
                    editors: editors,
                    onChange: () => { dirty = true; changed = true; }
                });
                results.find(".comment-toggle, .multi-value-add, .multi-value-remove").attr("tabindex", -1);

                if (isPanel) {
                    // The panel on one line with its status, and its tests beneath it
                    element.append(jq("<div>").addClass("lr-row")
                        .append(jq("<span>").addClass("lr-name").attr("title", order.orderNumber).html(testName(order)))
                        .append(jq("<span>")).append(jq("<span>")).append(statusCell));
                    element.append(results.addClass("lr-panel-members"));
                }
                else {
                    // A single test is one line: its name, result, normal range and status
                    const row = results.find(".result-row").first();
                    row.find(".test-name").html(testName(order)).attr("title", order.orderNumber);
                    row.append(statusCell);
                    element.append(results);
                }

                if (returnedForCorrection) {
                    element.append(jq("<div>").addClass("lr-note")
                        .text(messages.returnedForCorrection + ": " + (order.labResultValidation.comment || "")));
                }

                const reasonQuestion = labConfig.reasonTestNotPerformedQuestion;
                const reasonSection = jq("<div>").addClass("lr-reason")
                    .append(jq("<span>").addClass("lr-label").text(messages.reason + " "))
                    .append(formHelper.createObsWidget(reasonQuestion, {
                        id: idPrefix + "-reason",
                        orderUuid: order.uuid,
                        initialObsUuid: obsFor(reasonQuestion.uuid, order)?.uuid ?? null
                    }));
                element.append(reasonSection);

                // Each completed test records the result date chosen for the page
                const resultDateQuestion = labConfig.resultsDateQuestion;
                const resultDateWidget = formHelper.createObsWidget(resultDateQuestion, {
                    id: idPrefix + "-result-date",
                    orderUuid: order.uuid,
                    initialObsUuid: obsFor(resultDateQuestion.uuid, order)?.uuid ?? null
                });
                element.append(jq("<div>").addClass("lr-hidden").append(resultDateWidget));
                const resultDateField = resultDateWidget.find(".result-value-field");

                const status = () => statusWidget.val();
                const applyStatus = () => {
                    element.toggleClass("lr-not-performed", status() === 'EXCEPTION');
                    reasonSection.toggle(status() === 'EXCEPTION');
                };
                statusWidget.on("change", () => { dirty = true; changed = true; applyStatus(); });
                element.on("change input", ":input", () => { changed = true; });
                applyStatus();

                const resultFields = () => results.find(".result-value-field");
                return {
                    order: order,
                    element: element,
                    status: status,
                    setStatus: (value) => statusCell.find(".select-buttons button[data-value='" + value + "']").click(),
                    reasonValue: () => reasonSection.find(".result-value-field").val(),
                    editors: editors,
                    isChanged: () => changed,
                    hasResults: () => resultFields().filter((i, e) => jq(e).is(":checkbox") ? jq(e).is(":checked") : !!jq(e).val()).length > 0
                        || editors.some(e => e.hasValue()),
                    prepareForSave: (resultDate) => {
                        // Results are kept unless the test was not performed; the reason only when it was not
                        if (status() === 'EXCEPTION') {
                            results.find(":input").not("button").val("");
                            results.find(":checkbox").prop("checked", false);
                            editors.forEach(e => e.clear());
                            resultDateField.val("");
                        }
                        else {
                            reasonSection.find(":input").not("button").val("");
                            if (status() === 'COMPLETED') {
                                resultDateField.val(resultDate);
                            }
                        }
                    }
                };
            }

            // ---- Validating results: read only, a decision for each test (validate or return), saved together ----

            function renderValidation(group, orderables, validation) {
                const page = jq("<div>").addClass("lr-validate");
                const decisions = [];  // the tests this user can decide on
                const updateSummary = () => {
                    const pending = decisions.filter(d => !d.done);
                    summary.text(format(messages.decisionSummary,
                        pending.filter(d => d.value() === "VALIDATE").length, pending.filter(d => d.value() === "RETURN").length));
                };

                group.specimens.forEach(specimen => {
                    const encounter = specimen.encounter;
                    // The obs and the members of their groups: panels within panels are two levels deep
                    const flatten = (obsList) => (obsList || []).flatMap(o => [o, ...flatten(o.groupMembers)]);
                    const allObs = flatten(encounter.obs);
                    const card = jq("<div>").addClass("lr-specimen");
                    card.append(specimenHeader(specimen, (conceptUuid) => allObs.find(o => o.concept.uuid === conceptUuid)));

                    groupByCategory(specimen.orders).forEach(categoryGroup => {
                        const section = jq("<div>").addClass("lr-category");
                        const header = jq("<div>").addClass("lr-category-header")
                            .append(jq("<h4>").text(categoryGroup.category.displayStringForLab.toLowerCase()));
                        section.append(header);
                        section.append(jq("<div>").addClass("lr-row lr-columns")
                            .append(jq("<span>").text(messages.labTest))
                            .append(jq("<span>").text(messages.result))
                            .append(jq("<span>").text(messages.normalRange))
                            .append(jq("<span>").text(messages.enteredBy))
                            .append(jq("<span>").addClass("lr-status-cell").text(messages.decision)));

                        const categoryDecisions = [];
                        categoryGroup.orders.forEach(order => {
                            const info = validation[order.uuid] || {};
                            const orderable = orderables[order.concept.uuid];
                            const isPanel = PihAppsLabResultFields.isPanel(orderable, Object.assign({}, resultEditors, resultRenderers));
                            const tests = isPanel ? orderable.setMembers : [orderable];
                            const nameCell = jq("<span>").addClass("lr-name").attr("title", order.orderNumber).html(testName(order));
                            const enteredBy = jq("<span>").addClass("lr-entered-by").text((info.enteredBy || []).join(", "));
                            const decisionCell = jq("<span>").addClass("lr-status-cell");
                            const element = jq("<div>").addClass("lr-order");

                            // The result of the concept shown by its renderer extension, or null if the order has none
                            const renderedValue = (concept, gridColumn) => {
                                const obs = PihAppsLabResultFields.findResult(allObs, concept, order);
                                if (!obs) {
                                    return null;
                                }
                                const renderer = resultRenderers[concept.uuid];
                                const valueCell = jq("<span>").addClass("lr-value").css("grid-column", gridColumn);
                                PihAppsLabResultFields.renderResult(jq, renderer, obs.uuid)
                                    .then(content => valueCell.empty().append(content))
                                    .catch(error => console.warn("Unable to render the result with " + renderer.id, error));
                                return valueCell;
                            };

                            const valueRow = (concept, nameContent) => {
                                const values = allObs.filter(o => o.concept.uuid === concept.uuid && o.order?.uuid === order.uuid && !o.groupMembers);
                                const valueCell = jq("<span>").addClass("lr-value").html(values.map(o => displayValue(o, concept)).join("<br>"));
                                const rangeCell = jq("<span>").addClass("lr-range");
                                if (concept.datatype.name === 'Numeric' && values.length > 0) {
                                    PihAppsLabResultFields.fetchReferenceRange(jq, order, concept, (refRange) => {
                                        rangeCell.text(PihAppsLabResultFields.formatReferenceRange(refRange));
                                        // Flagged as on entry: the most severe of the values, beside the range
                                        const flags = values.map(o => PihAppsLabResultFields.validateNumericResult(messages, concept, refRange, o.valueNumeric)).filter(f => f);
                                        const flag = flags.find(f => f.type !== "abnormal") ?? flags[0];
                                        if (flag) {
                                            const type = flag.type === "abnormal" ? "abnormal" : "critical";
                                            valueCell.addClass(type === "critical" ? "lr-critical" : "lr-abnormal");
                                            rangeCell.append(jq("<span>").addClass("result-flag result-flag-" + type)
                                                .append(jq("<i>").addClass("fas fa-fw fa-exclamation-triangle")).append(document.createTextNode(flag.message)));
                                        }
                                    });
                                }
                                return { row: jq("<div>").addClass("lr-row").append(nameContent).append(valueCell).append(rangeCell), hasValues: values.length > 0 };
                            };

                            if (isPanel) {
                                element.append(jq("<div>").addClass("lr-row").append(nameCell).append(jq("<span>")).append(jq("<span>"))
                                    .append(enteredBy).append(decisionCell));
                                const members = jq("<div>").addClass("lr-panel-members");
                                tests.forEach(concept => {
                                    if (resultRenderers[concept.uuid]) {
                                        const valueCell = renderedValue(concept, "2 / -1");
                                        if (valueCell) {
                                            members.append(jq("<div>").addClass("lr-row")
                                                .append(jq("<span>").addClass("test-name").text(concept.displayStringForLab)).append(valueCell));
                                        }
                                        return;
                                    }
                                    const nestedPanel = PihAppsLabResultFields.isNestedPanel(concept, resultEditors) ? concept : null;
                                    if (nestedPanel) {
                                        const lines = nestedPanel.setMembers.map(m => valueRow(m, jq("<span>").addClass("test-name").text(m.displayStringForLab)))
                                            .filter(line => line.hasValues);
                                        if (lines.length > 0) {
                                            const section = jq("<div>").addClass("lr-subpanel")
                                                .append(jq("<div>").addClass("lr-row").append(jq("<span>").addClass("lr-subpanel-name").text(concept.displayStringForLab)));
                                            lines.forEach(line => section.append(line.row));
                                            members.append(section);
                                        }
                                        return;
                                    }
                                    const line = valueRow(concept, jq("<span>").addClass("test-name").text(concept.displayStringForLab));
                                    if (line.hasValues) {
                                        members.append(line.row);
                                    }
                                });
                                element.append(members);
                            }
                            else if (resultRenderers[tests[0].uuid]) {
                                const valueCell = renderedValue(tests[0], "span 2") || jq("<span>").css("grid-column", "span 2");
                                element.append(jq("<div>").addClass("lr-row").append(nameCell).append(valueCell).append(enteredBy).append(decisionCell));
                            }
                            else {
                                element.append(valueRow(tests[0], nameCell).row.append(enteredBy).append(decisionCell));
                            }

                            if (info.canValidate) {
                                const decision = renderDecision(order, element, decisionCell, updateSummary);
                                decisions.push(decision);
                                categoryDecisions.push(decision);
                            }
                            else if (info.enteredByCurrentUser) {
                                decisionCell.append(jq("<span>").addClass("lr-cannot-decide").text(messages.enteredByCurrentUser));
                            }
                            section.append(element);
                        });

                        if (categoryDecisions.length > 1) {
                            const markAll = jq("<button>").attr({ type: "button", tabindex: -1 }).text(format(messages.markAll, messages.decisionValidate));
                            markAll.on("click", () => categoryDecisions.filter(d => !d.done).forEach(d => d.set("VALIDATE")));
                            header.append(markAll);
                        }
                        card.append(section);
                    });
                    page.append(card);
                });

                const errors = jq("<div>").addClass("lr-errors");
                const summary = jq("<span>").addClass("lr-decision-summary");
                const saveButton = jq("<button>").addClass("confirm").text(messages.save);
                const backLink = jq("<a>").addClass("button").attr("href", labOrderListLink).text(messages.backToLabOrders);
                const actions = jq("<div>").addClass("lr-actions");
                if (decisions.length > 0) {
                    actions.append(saveButton).append(summary);
                }
                page.append(errors).append(actions.append(backLink));
                updateSummary();

                saveButton.on("click", () => {
                    const pending = decisions.filter(d => !d.done);
                    const toValidate = pending.filter(d => d.value() === "VALIDATE");
                    const toReturn = pending.filter(d => d.value() === "RETURN");
                    // Each test returned needs its reason
                    let reasonsMissing = false;
                    toReturn.forEach(d => {
                        const missing = !d.reason();
                        d.showReasonRequired(missing);
                        reasonsMissing = reasonsMissing || missing;
                    });
                    if (reasonsMissing) {
                        showErrors(page, [messages.reasonRequired]);
                        return;
                    }
                    showErrors(page, []);
                    saveButton.attr("disabled", "disabled");

                    // The tests validated together, with the results shown (the validation fails if they have changed
                    // since), then each test returned with its own reason. Each request is recorded on its own: if one
                    // fails, the tests already recorded are shown as done and left out when saving again.
                    const requests = [];
                    if (toValidate.length > 0) {
                        const results = {};
                        toValidate.forEach(d => { results[d.order.uuid] = validation[d.order.uuid]?.results ?? []; });
                        requests.push({ decisions: toValidate, body: { orders: toValidate.map(d => d.order.uuid), action: "VALIDATE", results: results } });
                    }
                    toReturn.forEach(d => requests.push({ decisions: [d], body: { orders: [d.order.uuid], action: "RETURN", comment: d.reason() } }));

                    requests.reduce((previous, request) => previous.then(() =>
                        postJson(rest + "pihapps/labResultValidation", request.body).then(() => request.decisions.forEach(d => d.markDone()))
                    ), Promise.resolve()).then(() => {
                        emr.successMessage(format(messages.decisionsSaved, toValidate.length, toReturn.length));
                        window.location.reload();
                    }).catch(xhr => {
                        saveButton.removeAttr("disabled");
                        updateSummary();
                        const done = decisions.filter(d => d.done && pending.includes(d)).map(d => d.order.concept.displayStringForLab);
                        showErrors(page, [errorMessage(xhr)].concat(done.length > 0 ? [format(messages.alreadyRecorded, done.join(", "))] : []));
                    });
                });
                return page;
            }

            // The decision on one test: validate (the default) or return, with a reason, for correction
            function renderDecision(order, element, decisionCell, onChange) {
                let value = "VALIDATE";
                let done = false;
                const button = (decision, label) => jq("<button>").attr({ type: "button", tabindex: -1, "data-value": decision })
                    .addClass("btn btn-outline-secondary btn-sm").text(label);
                const validateButton = button("VALIDATE", messages.decisionValidate);
                const returnButton = button("RETURN", messages.decisionReturn);
                decisionCell.append(jq("<span>").addClass("select-buttons lr-decision").append(validateButton).append(returnButton));

                const reasonField = jq("<textarea>").attr({ rows: 2, maxlength: 1000 });
                const reasonRequired = jq("<span>").addClass("lr-reason-required").text(messages.reasonRequired).hide();
                const reasonSection = jq("<div>").addClass("lr-return-reason")
                    .append(jq("<label>").text(messages.returnReason)).append(reasonField).append(reasonRequired).hide();
                element.append(reasonSection);
                const doneNote = jq("<div>").addClass("lr-note lr-done-note").hide();
                element.append(doneNote);

                const set = (decision) => {
                    if (done) {
                        return;
                    }
                    value = decision;
                    validateButton.toggleClass("active", decision === "VALIDATE");
                    returnButton.toggleClass("active", decision === "RETURN");
                    reasonSection.toggle(decision === "RETURN");
                    if (decision === "RETURN") {
                        reasonField.focus();
                    }
                    else {
                        reasonRequired.hide();
                    }
                    onChange();
                };
                validateButton.on("click", () => set("VALIDATE"));
                returnButton.on("click", () => set("RETURN"));
                reasonField.on("input", () => {
                    if (reasonField.val().trim()) {
                        reasonRequired.hide();
                        if (element.closest(".lr-validate").find(".lr-reason-required:visible").length === 0) {
                            element.closest(".lr-validate").find(".lr-errors").first().empty();
                        }
                    }
                });
                validateButton.addClass("active");

                return {
                    order: order,
                    value: () => value,
                    set: set,
                    reason: () => reasonField.val().trim(),
                    showReasonRequired: (show) => reasonRequired.toggle(show),
                    get done() { return done; },
                    markDone: () => {
                        done = true;
                        element.addClass("lr-saved");
                        element.find("button, textarea").prop("disabled", true);
                        doneNote.text(value === "VALIDATE" ? messages.validated : messages.returnedForCorrection).show();
                    }
                };
            }

            function displayValue(obs, concept) {
                let value;
                if (obs.valueCoded) {
                    value = obs.valueCoded.display;
                }
                else if (obs.valueNumeric != null) {
                    value = obs.valueNumeric + (concept.units ? " " + concept.units : "");
                }
                else if (obs.valueDatetime) {
                    value = dateUtils.formatDateWithTimeIfPresent(obs.valueDatetime);
                }
                else {
                    value = obs.valueText ?? (typeof obs.value === 'object' ? obs.value?.display : obs.value) ?? "";
                }
                return escapeHtml(value) + (obs.comment ? ' <span class="lr-entered-by">(' + escapeHtml(obs.comment) + ')</span>' : '');
            }
        }).catch(error => {
            console.error(error);
            jq("#lr-loading").hide();
            jq("#lr-content").append(jq("<div>").addClass("lr-errors").text(messages.unableToLoad));
        });
    });
</script>
<% } %>
