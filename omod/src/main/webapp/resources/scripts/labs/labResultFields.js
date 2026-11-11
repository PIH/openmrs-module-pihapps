/**
 * Renders the result entry fields for one lab order: one row per test, or one row per member test of a panel.
 * Shared by the single order results form and by results forms that enter several orders on one page.
 * Requires formHelper.js and an initialized FormHelper whose encounter holds any existing results.
 *
 * A result recorded as an obs group, such as the organisms a culture isolated and their antibiotic susceptibility,
 * can be entered with an implementation's own editor: an extension of
 * org.openmrs.module.pihapps.labResults.obsGroupEditors whose extensionParams.concept is the group's concept (a member
 * of the ordered panel) and whose extensionParams.jsFunction names a function, on window, that is given
 * (context, extensionParams) and returns, or resolves to, an editor.
 *   context: { jq, order, concept, orderable, container, idPrefix, onChange }
 *     order - the order, with its fulfillerEncounter; concept - the group concept; orderable - the ordered concept;
 *     container - the element holding the order's result rows, each a .result-row with data-concept-uuid;
 *     onChange - to call when the editor's value changes
 *   editor: { element, hasValue(), isChanged(), clear(), save(encounterUuid) }
 *     element - shown as the result of the row; save - records the value in the encounter fulfilling the order and
 *     returns a promise. Forms call save, for changed editors only, before saving the order's other results, so that
 *     a failed save leaves nothing half saved; saving again must replace what an earlier save recorded.
 * The editor saves its own obs. They are results of the order, so the editor links them to the order and gives them
 * the status PihAppsService.getNewLabResultStatus returns, for them to be validated with the order's other results.
 * A result that replaces a FINAL or AMENDED one is AMENDED, with the replaced obs as its previous version.
 * Only the direct members of the ordered panel can have an editor, not the members of a panel within it.
 */
class PihAppsLabResultFields {

    static get OBS_GROUP_EDITORS_EXTENSION_POINT() { return "org.openmrs.module.pihapps.labResults.obsGroupEditors"; }

    /**
     * @return the obs group editor extensions whose function is defined, by concept uuid
     */
    static getObsGroupEditors(extensions) {
        const ret = {};
        (extensions || []).forEach(ext => {
            const params = ext.extensionParams || {};
            if (params.concept && typeof window[params.jsFunction] === 'function') {
                ret[params.concept] = ext;
            }
            else {
                console.warn("Obs group editor " + ext.id + " needs extensionParams.concept and a function named by extensionParams.jsFunction");
            }
        });
        return ret;
    }

    /**
     * Saves the changed editors, one after the other, in the encounter
     * @return a promise
     */
    static saveEditors(editors, encounterUuid) {
        return (editors || []).reduce((previous, editor) => previous.then(() => editor.save(encounterUuid)), Promise.resolve());
    }

    /**
     * Creates the editor of an obs group result in the widget section of its row
     * @return an editor that can be used before the extension's editor is ready: until then it has no value
     */
    static createObsGroupEditor(jq, extension, context, widgetSection, messages) {
        let editor = null;
        let clearWhenReady = false;  // cleared, eg by marking the test not performed, before the editor was ready
        const ready = Promise.resolve()
            .then(() => window[extension.extensionParams.jsFunction](context, extension.extensionParams))
            .then(created => {
                editor = created;
                widgetSection.append(editor.element);
                if (clearWhenReady) {
                    editor.clear();
                }
                return editor;
            })
            .catch(error => {
                console.error("Obs group editor " + extension.id, error);
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
            save: (encounterUuid) => ready.then(e => (e && e.isChanged()) ? e.save(encounterUuid) : null)
        };
    }

    /**
     * The representation of the ordered concept that render() needs
     */
    static get orderableRep() {
        const baseConceptRep = "uuid,display,displayStringForLab,datatype:(uuid,name),allowDecimal,units,multipleAnswer,fieldDependencyRule";
        const baseConceptRepWithAnswers = baseConceptRep + ",answers:(" + baseConceptRep + ")";
        return baseConceptRepWithAnswers + ",setMembers:(" + baseConceptRepWithAnswers + ")";
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
            if (refRange.lowAbsolute && value < refRange.lowAbsolute) {
                return {type: "error", message: messages.minimumAllowedValue + ": " + refRange.lowAbsolute};
            }
            if (refRange.hiAbsolute && value > refRange.hiAbsolute) {
                return {type: "error", message: messages.maximumAllowedValue + ": " + refRange.hiAbsolute};
            }
            if ((refRange.lowCritical && value < refRange.lowCritical) || (refRange.hiCritical && value > refRange.hiCritical)) {
                return {type: "critical", message: messages.criticalValue};
            }
            if ((refRange.lowNormal && value < refRange.lowNormal) || (refRange.hiNormal && value > refRange.hiNormal)) {
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

    static showValidationResult(jq, messages, concept, refRange, textbox) {
        const validationError = PihAppsLabResultFields.validateNumericResult(messages, concept, refRange, textbox.val());
        const fieldErrorDiv = textbox.siblings(".field-error");
        fieldErrorDiv.html("").removeClass("abnormal-value").removeClass("critical-value").removeClass("error-value");
        if (validationError) {
            fieldErrorDiv.html(validationError.message).addClass(validationError.type + "-value");
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
     * config.obsGroupEditors - optional, the obs group editor extensions by concept uuid (see getObsGroupEditors)
     * config.editors - optional, an array the editors of obs group results are added to
     * config.onChange - optional, called when an obs group editor's value changes
     */
    static render(config) {
        const jq = config.jq;
        const formHelper = config.formHelper;
        const order = config.order;
        const orderable = config.orderable;
        const id = config.idPrefix;
        const messages = config.messages;
        const obsGroupEditors = config.obsGroupEditors || {};

        const isPanel = orderable.setMembers.length > 0;
        const tests = isPanel ? orderable.setMembers : [orderable];
        tests.forEach((concept) => {
            const orderableRow = jq("<div>").addClass("form-field-section row result-row align-items-start").attr("data-concept-uuid", concept.uuid);
            config.container.append(orderableRow);
            const testNameSection = jq("<span>").addClass("test-name col-3").append(concept.displayStringForLab);
            orderableRow.append(testNameSection);

            const widgetSection = jq("<span>").addClass("form-field-widgets col-6");
            const widgetInfoSection = jq("<span>").addClass("form-field-widgets col-3");
            orderableRow.append(widgetSection);
            orderableRow.append(widgetInfoSection);

            const editorExtension = isPanel ? obsGroupEditors[concept.uuid] : null;
            if (editorExtension) {
                orderableRow.addClass("obs-group-editor-row");
                const editor = PihAppsLabResultFields.createObsGroupEditor(jq, editorExtension, {
                    jq: jq,
                    order: order,
                    concept: concept,
                    orderable: orderable,
                    container: config.container,
                    idPrefix: id,
                    onChange: () => { if (config.onChange) { config.onChange(); } }
                }, widgetSection, messages);
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
                        widgetSection.on("focusout", ".result-numeric-input", (event) => {
                            PihAppsLabResultFields.showValidationResult(jq, messages, concept, refRange, jq(event.target));
                        });
                        widgetSection.find(".result-numeric-input").trigger("focusout");
                        const refRangeDisplay = PihAppsLabResultFields.formatReferenceRange(refRange);
                        widgetInfoSection.append(jq("<p>").append(jq("<span>").addClass("reference-range").html(refRangeDisplay)));
                    });
                }

            } else {
                const singleObs = formHelper.initialObs.find(o => o.concept.uuid === concept.uuid && o.order?.uuid === order.uuid)
                    ?? formHelper.initialObs.find(o => o.concept.uuid === concept.uuid && !o.order);
                const widget = formHelper.createObsWidget(concept, {
                    id: id + concept.uuid,
                    orderUuid: order.uuid,
                    groupingConceptUuid: isPanel ? orderable.uuid : null,
                    initialObsUuid: singleObs?.uuid ?? null,
                    withComment: config.collectResultComments,
                    addCommentLabel: messages.addComment,
                    removeCommentLabel: messages.removeComment
                });
                const widgetWrapper = jq("<div>").addClass("lab-results-widget").append(widget);
                widgetSection.append(widgetWrapper);

                const referenceRangeSection = jq("<span>").addClass("reference-range");
                if (concept.datatype.name === 'Numeric') {
                    PihAppsLabResultFields.fetchReferenceRange(jq, order, concept, (refRange) => {
                        widgetSection.find(".result-numeric-input").on("blur", (event) => {
                            PihAppsLabResultFields.showValidationResult(jq, messages, concept, refRange, jq(event.target));
                        }).blur();
                        referenceRangeSection.html(PihAppsLabResultFields.formatReferenceRange(refRange));
                    });
                }
                widgetInfoSection.append(jq("<p>").append(referenceRangeSection));
            }
        });

        const fieldTests = tests.filter(concept => !(isPanel && obsGroupEditors[concept.uuid]));
        formHelper.wireFieldDependencyRules(config.container, fieldTests);
        return fieldTests;
    }
}
