/**
 * Renders the result entry fields for one lab order: one row per test, or one row per member test of a panel.
 * Shared by the single order results form and by results forms that enter several orders on one page.
 * Requires formHelper.js and an initialized FormHelper whose encounter holds any existing results.
 *
 * A result can be shown and entered by an implementation's own JavaScript instead of the standard fields, such as the
 * organisms a culture isolated and their antibiotic susceptibility, recorded as an obs group.  The JavaScript is
 * registered as extensions whose extensionParams.concept is the result concept and whose extensionParams.jsFunction
 * names a function on window.  The result can be a single obs or an obs group.  An editor of a result recorded as an obs
 * group needs a renderer too: without one, the validate view and the patient lab results page do not show the group.
 *
 * org.openmrs.module.pihapps.labResults.resultRenderers: jsFunction(obs, extensionParams) returns, or resolves
 * to, the content shown for the result, on the patient lab results page and when validating results.
 *   obs - the result as loaded with renderResult: uuid, concept, values, status, order and, for a group, groupMembers
 *   at every level (see resultRep)
 *
 * org.openmrs.module.pihapps.labResults.resultEditors: jsFunction(context, extensionParams) returns, or resolves to, an
 * editor of the result, on the results forms.  The concept is the ordered test, or a direct member of the ordered
 * panel; an ordered concept with an editor is entered as one result, even if it has set members.
 *   context: { jq, order, concept, orderable, obs, container, idPrefix, onChange }
 *     order - the order, with its fulfillerEncounter; concept - the result concept; orderable - the ordered concept;
 *     obs - the order's existing result of the concept in the encounter, as given to renderers, or null;
 *     container - the element holding the order's result rows, each a .result-row with data-concept-uuid;
 *     onChange - to call when the editor's value changes
 *   editor: { element, hasValue(), isChanged(), clear(), getObs() }
 *     element - shown as the result of the row; getObs - the result to record, as a new obs in the representation the
 *     obs resource accepts ({ concept, value } or { concept, groupMembers: [{ concept, valueCoded, groupMembers }] }),
 *     without the uuids of existing obs (the server rejects them), or null to remove the result
 * The editor does not save anything: the form sends the results of changed editors with the order's other results, in
 * one request (EncounterFulfillingOrders.editorResults).  The server replaces the order's existing result of the
 * concept with it, links it and its members to the order, and gives them the status of the order's other new results:
 * preliminary when they await validation, and amended, with the replaced result as previous version, when they replace
 * a final one.
 */
class PihAppsLabResultFields {

    /**
     * @return the result editor extensions whose function is defined, by concept uuid
     */
    static getResultEditors(extensions) {
        return PihAppsLabResultFields.byConcept(extensions, "Result editor");
    }

    /**
     * @return the result renderer extensions whose function is defined, by concept uuid
     */
    static getResultRenderers(extensions) {
        return PihAppsLabResultFields.byConcept(extensions, "Result renderer");
    }

    static byConcept(extensions, kind) {
        const ret = {};
        (extensions || []).forEach(ext => {
            const params = ext.extensionParams || {};
            if (params.concept && typeof window[params.jsFunction] === 'function') {
                ret[params.concept] = ext;
            }
            else {
                console.warn(kind + " " + ext.id + " needs extensionParams.concept and a function named by extensionParams.jsFunction");
            }
        });
        return ret;
    }

    /**
     * The representation of a result given to renderers and editors: for a group, its members at every level, up to four
     */
    static get resultRep() {
        const obsRep = "uuid,concept:(uuid,display,displayStringForLab),value,valueCoded:(uuid,display,displayStringForLab)," +
            "valueNumeric,valueText,valueDatetime,comment,status,obsDatetime,order:(uuid)";
        let rep = obsRep;
        for (let i = 0; i < 4; i++) {
            rep = obsRep + ",groupMembers:(" + rep + ")";
        }
        return rep;
    }

    /**
     * Loads the result obs and renders it with the renderer extension
     * @return a promise of the content
     */
    static renderResult(jq, extension, obsUuid) {
        return PihAppsLabResultFields.loadResult(jq, obsUuid)
            .then(obs => window[extension.extensionParams.jsFunction](obs, extension.extensionParams));
    }

    /**
     * @return a promise of the result obs, in resultRep
     */
    static loadResult(jq, obsUuid) {
        return new Promise((resolve, reject) => jq.getJSON(openmrsContextPath + "/ws/rest/v1/obs/" + obsUuid
            + "?v=custom:(" + PihAppsLabResultFields.resultRep + ")").done(resolve).fail(reject));
    }

    /**
     * @return the order's result of the concept among the obs, which may include the members of groups, else undefined
     */
    static findResult(obsList, concept, order) {
        return (obsList || []).find(o => o.concept.uuid === concept.uuid && o.order?.uuid === order.uuid);
    }

    /**
     * @return whether the ordered concept is a panel, whose members are entered as its tests, rather than one result
     */
    static isPanel(orderable, resultEditors) {
        return (orderable.setMembers || []).length > 0 && !(resultEditors || {})[orderable.uuid];
    }

    /**
     * @return the results of the changed editors, for EncounterFulfillingOrders.editorResults
     */
    static getEditorResults(editors) {
        return (editors || []).map(editor => editor.getResult()).filter(result => result);
    }

    /**
     * Creates the editor of a result in the widget section of its row
     * @return an editor that can be used before the extension's editor is ready: until then it has no value
     */
    static createResultEditor(jq, extension, context, existingObsUuid, widgetSection, messages) {
        let editor = null;
        let clearWhenReady = false;  // cleared, eg by marking the test not performed, before the editor was ready
        const ready = (existingObsUuid ? PihAppsLabResultFields.loadResult(jq, existingObsUuid) : Promise.resolve(null))
            .then(obs => window[extension.extensionParams.jsFunction](Object.assign({}, context, { obs: obs }), extension.extensionParams))
            .then(created => {
                editor = created;
                widgetSection.append(editor.element);
                if (clearWhenReady) {
                    editor.clear();
                }
                return editor;
            })
            .catch(error => {
                console.error("Result editor " + extension.id, error);
                widgetSection.append(jq("<span>").addClass("field-error").text(messages.unableToLoadResultEntry ?? "Unable to load"));
                return null;
            });
        return {
            concept: context.concept,
            ready: ready,
            hasValue: () => !!editor && editor.hasValue(),
            isChanged: () => !!editor && editor.isChanged(),
            clear: () => {
                if (editor) {
                    editor.clear();
                }
                else {
                    clearWhenReady = true;
                }
            },
            // The result to send for a changed editor: { order, concept, obs }, else null
            getResult: () => (editor && editor.isChanged())
                ? { order: context.order.uuid, concept: context.concept.uuid, obs: editor.getObs() }
                : null
        };
    }

    /**
     * The representation of the ordered concept that render() needs
     */
    static get orderableRep() {
        const baseConceptRep = "uuid,display,displayStringForLab,datatype:(uuid,name),allowDecimal,units,multipleAnswer,fieldDependencyRule";
        const baseConceptRepWithAnswers = baseConceptRep + ",answers:(" + baseConceptRep + ")";
        // Members of the ordered concept, and of its members that are panels themselves
        return baseConceptRepWithAnswers + ",setMembers:(" + baseConceptRepWithAnswers + ",setMembers:(" + baseConceptRepWithAnswers + "))";
    }

    /**
     * @return whether a member of a panel is a panel itself, rendered as a heading and its own tests, rather than a field
     */
    static isNestedPanel(concept, resultEditors) {
        return concept.datatype?.name === "N/A" && (concept.setMembers || []).length > 0 && !(resultEditors || {})[concept.uuid];
    }

    /**
     * Returns a validation result for a numeric field - with result type of [error, critical, abnormal], and message
     */
    static validateNumericResult(messages, concept, refRange, value) {
        if (value && refRange) {
            const numericValue = +value;
            if (!concept.allowDecimal && !Number.isInteger(numericValue)) {
                return {type: "error", message: messages.valueMustBeInteger};
            }
            // A limit of 0 is a limit: compare with null, not truthiness
            const below = (limit) => limit != null && numericValue < limit;
            const above = (limit) => limit != null && numericValue > limit;
            if (below(refRange.lowAbsolute)) {
                return {type: "error", message: messages.minimumAllowedValue + ": " + refRange.lowAbsolute};
            }
            if (above(refRange.hiAbsolute)) {
                return {type: "error", message: messages.maximumAllowedValue + ": " + refRange.hiAbsolute};
            }
            if (below(refRange.lowCritical) || above(refRange.hiCritical)) {
                return {type: "critical", message: messages.criticalValue};
            }
            if (below(refRange.lowNormal) || above(refRange.hiNormal)) {
                return {type: "abnormal", message: messages.abnormalValue};
            }
        }
        return null;
    }

    static formatReferenceRange(refRange) {
        return !refRange ? "" :
            refRange.lowNormal && refRange.hiNormal ? (refRange.lowNormal + " - " + refRange.hiNormal) :
                refRange.lowNormal ? (">= " + refRange.lowNormal) :
                    refRange.hiNormal ? ("=< " + refRange.hiNormal) : "";
    }

    /**
     * Shows the validation of the given numeric fields of one test.  An invalid value is shown under its field, as it
     * prevents saving.  Abnormal and critical values color their field, and the most severe of them is flagged in
     * flagElement (beside the normal range), so that the widths of the fields and what follows them do not change.
     */
    static showValidationResult(jq, messages, concept, refRange, textboxes, flagElement) {
        let flag = null;
        textboxes.each((i, element) => {
            const textbox = jq(element);
            const validationError = PihAppsLabResultFields.validateNumericResult(messages, concept, refRange, textbox.val());
            const fieldErrorDiv = textbox.siblings(".field-error");
            fieldErrorDiv.html("").removeClass("abnormal-value critical-value error-value");
            textbox.removeClass("result-abnormal result-critical result-invalid");
            if (validationError?.type === "error") {
                fieldErrorDiv.html(validationError.message).addClass("error-value");
                textbox.addClass("result-invalid");
            }
            else if (validationError) {
                textbox.addClass("result-" + validationError.type);
                if (!flag || validationError.type === "critical") {
                    flag = validationError;
                }
            }
        });
        if (flagElement) {
            flagElement.removeClass("result-flag-abnormal result-flag-critical").empty();
            if (flag) {
                flagElement.addClass("result-flag-" + flag.type)
                    .append(jq("<i>").addClass("fas fa-fw fa-exclamation-triangle"))
                    .append(document.createTextNode(flag.message));
            }
        }
    }

    static fetchReferenceRange(jq, order, concept, callback) {
        const refRangeRep = "hiNormal,hiAbsolute,hiCritical,lowNormal,lowAbsolute,lowCritical";
        const refRangeQuery = "patient=" + order.patient.uuid + "&encounter=" + order.encounter.uuid + "&concept=" + concept.uuid;
        jq.get(openmrsContextPath + "/ws/rest/v1/conceptreferencerange?" + refRangeQuery + "&v=custom:(" + refRangeRep + ")", function (data) {
            callback(data.results && data.results.length > 0 ? data.results[0] : null);
        });
    }

    /**
     * Appends a result row for each test of the order to the container, and returns the tests rendered
     *
     * config.jq - jQuery
     * config.formHelper - the FormHelper of the encounter the results are recorded in
     * config.order - the order, with patient, encounter and concept
     * config.orderable - the ordered concept, fetched with orderableRep
     * config.container - the element to append the rows to
     * config.idPrefix - prefix for the ids of the widgets, unique among the orders on the page
     * config.collectResultComments - whether a comment can be entered with each result
     * config.messages - the messages used by the results form
     * config.resultEditors - optional, the result editor extensions by concept uuid (see getResultEditors)
     * config.editors - optional, an array the editors are added to
     * config.onChange - optional, called when a result editor's value changes
     */
    static render(config) {
        const jq = config.jq;
        const formHelper = config.formHelper;
        const order = config.order;
        const orderable = config.orderable;
        const id = config.idPrefix;
        const messages = config.messages;
        const resultEditors = config.resultEditors || {};

        const isPanel = PihAppsLabResultFields.isPanel(orderable, resultEditors);
        const tests = isPanel ? orderable.setMembers : [orderable];
        tests.forEach((concept) => {
            // A member that is a panel itself: a heading, then its tests, recorded in their own obs group, in the group of the panel
            if (isPanel && PihAppsLabResultFields.isNestedPanel(concept, resultEditors)) {
                const section = jq("<div>").addClass("result-subpanel lr-subpanel");
                section.append(jq("<div>").addClass("result-subpanel-heading lr-row")
                    .append(jq("<span>").addClass("result-subpanel-name lr-subpanel-name").text(concept.displayStringForLab)));
                config.container.append(section);
                PihAppsLabResultFields.render(Object.assign({}, config, {
                    orderable: concept,
                    parentGroupingConceptUuid: orderable.uuid,
                    container: section,
                    idPrefix: id + concept.uuid + "-",
                    resultEditors: {}
                }));
                return;
            }
            const orderableRow = jq("<div>").addClass("form-field-section row result-row align-items-start").attr("data-concept-uuid", concept.uuid);
            config.container.append(orderableRow);
            const testNameSection = jq("<span>").addClass("test-name col-3").append(concept.displayStringForLab);
            orderableRow.append(testNameSection);

            const widgetSection = jq("<span>").addClass("form-field-widgets col-6");
            const widgetInfoSection = jq("<span>").addClass("form-field-widgets col-3");
            orderableRow.append(widgetSection);
            orderableRow.append(widgetInfoSection);

            const editorExtension = resultEditors[concept.uuid];
            if (editorExtension) {
                orderableRow.addClass("result-editor-row");
                const existing = PihAppsLabResultFields.findResult(formHelper.initialObs, concept, order);
                const editor = PihAppsLabResultFields.createResultEditor(jq, editorExtension, {
                    jq: jq,
                    order: order,
                    concept: concept,
                    orderable: orderable,
                    container: config.container,
                    idPrefix: id,
                    onChange: () => { if (config.onChange) { config.onChange(); } }
                }, existing?.uuid, widgetSection, messages);
                if (config.editors) {
                    config.editors.push(editor);
                }
            }
            else if (concept.multipleAnswer) {
                const orderLinkedObs = formHelper.initialObs.filter(o => o.concept.uuid === concept.uuid && o.order?.uuid === order.uuid);
                const existingObs = (orderLinkedObs.length > 0 ? orderLinkedObs :
                    formHelper.initialObs.filter(o => o.concept.uuid === concept.uuid && !o.order))
                    .sort((a, b) => formHelper.pathIndex(a.formNamespaceAndPath) - formHelper.pathIndex(b.formNamespaceAndPath));
                const existingIndices = existingObs.map(o => formHelper.pathIndex(o.formNamespaceAndPath)).filter(i => i >= 0);
                const obsToRender = existingObs.length > 0 ? existingObs : [null];
                let nextPathIndex = existingIndices.length > 0 ? Math.max(...existingIndices) + 1 : 0;

                // For coded concepts: keep each option available in at most one row at a time
                const syncOptions = concept.datatype.name === 'Coded' ? () => {
                    const selects = widgetSection.find('select.result-value-field');
                    const selectedValues = new Set(
                        selects.map(function() { return jq(this).val(); }).get().filter(v => v)
                    );
                    selects.each(function() {
                        const ownValue = jq(this).val();
                        jq(this).find('option').each(function() {
                            const val = jq(this).val();
                            const takenByOther = val && val !== ownValue && selectedValues.has(val);
                            jq(this).prop('disabled', takenByOther).toggle(!takenByOther);
                        });
                    });
                } : null;

                const renderMultiValueRow = (obsForRow, isFirst) => {
                    const rowDiv = jq("<div>").addClass("d-flex align-items-start gap-2 multi-value-row");
                    const widget = formHelper.createObsWidget(concept, {
                        id: id + concept.uuid + (obsForRow ? '_' + obsForRow.uuid : '_new_' + formHelper.generateUUID()),
                        orderUuid: order.uuid,
                        groupingConceptUuid: isPanel ? orderable.uuid : null,
                        parentGroupingConceptUuid: isPanel ? (config.parentGroupingConceptUuid ?? null) : null,
                        initialObsUuid: obsForRow?.uuid ?? null,
                        pathIndex: obsForRow ? undefined : nextPathIndex++,
                        withComment: config.collectResultComments,
                        addCommentLabel: messages.addComment,
                        removeCommentLabel: messages.removeComment
                    });
                    rowDiv.append(widget);

                    if (!isFirst) {
                        const removeBtn = jq("<button>")
                            .addClass("btn btn-sm btn-outline-secondary multi-value-remove py-0 px-1")
                            .attr("type", "button")
                            .attr("aria-label", messages.removeResult)
                            .text('×');
                        removeBtn.on("click", () => {
                            rowDiv.find(".result-value-field").val("");
                            rowDiv.find(".result-comment").val("");
                            rowDiv.remove();
                            if (syncOptions) syncOptions();
                        });
                        rowDiv.append(removeBtn);
                    }
                    return rowDiv;
                };

                obsToRender.forEach((obs, index) => {
                    widgetSection.append(renderMultiValueRow(obs, index === 0));
                });

                if (syncOptions) {
                    syncOptions();
                    widgetSection.on('change', 'select.result-value-field', syncOptions);
                }

                const addBtn = jq("<button>")
                    .addClass("btn btn-sm btn-outline-secondary multi-value-add mt-1")
                    .attr("type", "button")
                    .text(messages.addAnotherResult);
                addBtn.on("click", () => {
                    addBtn.before(renderMultiValueRow(null, false));
                    if (syncOptions) syncOptions();
                });
                widgetSection.append(addBtn);

                if (concept.datatype.name === 'Numeric') {
                    PihAppsLabResultFields.fetchReferenceRange(jq, order, concept, (refRange) => {
                        const flag = jq("<span>").addClass("result-flag");
                        const validate = () => PihAppsLabResultFields.showValidationResult(jq, messages, concept, refRange,
                            widgetSection.find(".result-numeric-input"), flag);
                        widgetSection.on("focusout", ".result-numeric-input", validate);
                        widgetSection.on("click", ".multi-value-remove", () => setTimeout(validate));
                        const refRangeDisplay = PihAppsLabResultFields.formatReferenceRange(refRange);
                        widgetInfoSection.append(jq("<p>").append(jq("<span>").addClass("reference-range").html(refRangeDisplay)).append(flag));
                        validate();
                    });
                }

            } else {
                const singleObs = formHelper.initialObs.find(o => o.concept.uuid === concept.uuid && o.order?.uuid === order.uuid)
                    ?? formHelper.initialObs.find(o => o.concept.uuid === concept.uuid && !o.order);
                const widget = formHelper.createObsWidget(concept, {
                    id: id + concept.uuid,
                    orderUuid: order.uuid,
                    groupingConceptUuid: isPanel ? orderable.uuid : null,
                    parentGroupingConceptUuid: isPanel ? (config.parentGroupingConceptUuid ?? null) : null,
                    initialObsUuid: singleObs?.uuid ?? null,
                    withComment: config.collectResultComments,
                    addCommentLabel: messages.addComment,
                    removeCommentLabel: messages.removeComment
                });
                const widgetWrapper = jq("<div>").addClass("lab-results-widget").append(widget);
                widgetSection.append(widgetWrapper);

                const referenceRangeSection = jq("<span>").addClass("reference-range");
                const flag = jq("<span>").addClass("result-flag");
                if (concept.datatype.name === 'Numeric') {
                    PihAppsLabResultFields.fetchReferenceRange(jq, order, concept, (refRange) => {
                        const textbox = widgetSection.find(".result-numeric-input");
                        const validate = () => PihAppsLabResultFields.showValidationResult(jq, messages, concept, refRange, textbox, flag);
                        textbox.on("blur", validate);
                        referenceRangeSection.html(PihAppsLabResultFields.formatReferenceRange(refRange));
                        validate();
                    });
                }
                widgetInfoSection.append(jq("<p>").append(referenceRangeSection).append(flag));
            }
        });

        const fieldTests = tests.filter(concept => !(resultEditors[concept.uuid] || (isPanel && PihAppsLabResultFields.isNestedPanel(concept, resultEditors))));
        formHelper.wireFieldDependencyRules(config.container, fieldTests);
        return fieldTests;
    }
}
