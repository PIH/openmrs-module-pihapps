/**
 * Helper class to support form entry
 */
class FormHelper {

    constructor(args) {
        this.jq = args.jq;
        this.moment = args.moment;
        this.locale = args.locale;
        this.dateFormat = args.dateFormat;
        this.dateTimeFormat = args.dateTimeFormat;
        this.formName = args.formName;
        this.encounter = args.encounter;
        this.patient = this.encounter?.patient ?? args.patient ?? null;
        this.date = this.encounter?.encounterDatetime ?? args.date ?? new Date();
        this.initialObs = this.encounter?.obs?.flatMap(o => { return o.groupMembers ? [o, ...o.groupMembers] : o }) ?? [];
        this.obsWidgetFields = [];
    }

    getInitialObsValues(conceptUuid) {
        return this.initialObs.filter((o) => o.concept.uuid === conceptUuid) ?? [];
    }

    // TODO: Support multiple values for the same concept
    getInitialObsValue(conceptUuid) {
        return this.getInitialObsValues(conceptUuid)[0] ?? null;
    }

    /**
     * @param concept an object representation of the concept, at minimum (uuid,datatype:(name),answers:(uuid,display),units
     * @param options - supported properties include { id, name, valueSet, groupingConceptUuid, orderUuid }
     */
    createObsWidget = function(concept, options) {
        const widget = jq("<span>").addClass("obs-widget");
        const dataType = concept.datatype?.name;
        const initialObs = this.getInitialObsValue(concept.uuid);

        // Get possible values if this is meant to be coded
        const valueSet = options.valueSet ?? (dataType === 'Coded' && concept.answers?.length > 0 ? concept.answers.map(a => {
            return { value: a.uuid, display: a.display}
        }) : []) ?? [];

        let widgetField = null;

        if (valueSet.length > 0) {
            const initialValue = initialObs?.value;
            widgetField = this.createSelectWidget({
                id: options?.id,
                name: options?.name,
                options: valueSet,
                initialValue: initialValue?.uuid ?? initialValue ?? ""}
            )
            widget.append(widgetField);
        }
        else if (dataType === "Numeric") {
            widgetField = jq("<input>");
            if (options?.id) {
                widgetField.attr("id", options.id);
            }
            if (options?.name) {
                widgetField.attr("name", options.name);
            }
            widgetField.addClass("result-numeric-input").attr("type", "number").attr("size", "10");
            widgetField.val(initialObs?.valueNumeric ?? initialObs?.value ?? "");
            widget.append(widgetField);
            widget.append(jq("<span>").addClass("result-units").html(concept.units ?? ""));
        }
        else if (dataType === "Text") {
            widgetField = jq("<input>");
            if (options?.id) {
                widgetField.attr("id", options.id);
            }
            if (options?.name) {
                widgetField.attr("name", options.name);
            }
            widgetField.addClass("result-text-input").attr("type", "text").attr("size", "30");
            widgetField.val(initialObs?.valueText ?? initialObs?.value ?? "");
            widget.append(widgetField);
        }
        else if (dataType === "Date" || dataType === "Datetime") {
            const id = options?.id ?? crypto.randomUUID();
            const datePicker = this.createDatePickerWidget({
                id: id,
                locale: this.locale,
                useTime: dataType === 'Datetime',
                initialValue: initialObs?.valueDatetime
            });
            widgetField = datePicker.find("#" + id + "-field");
            if (options?.name) {
                widgetField.attr("name", options.name);
            }
            widget.append(datePicker);
        }
        else {
            widget.addClass("error").append("Unable to handle concept of type: " + dataType);
        }

        if (widgetField) {
            widgetField.addClass("result-value-field")

            widgetField.attr("data-concept-uuid", concept.uuid);
            widgetField.attr("data-concept-datatype", concept.datatype.name);
            if (options?.groupingConceptUuid) {
                widgetField.attr("data-grouping-concept-uuid", options.groupingConceptUuid);
            }
            widgetField.attr("data-form-path", "/" + (options.groupingConceptUuid ? options.groupingConceptUuid + "/" : "") + concept.uuid)
            if (this.patient) {
                widgetField.attr("data-patient-uuid", this.patient?.uuid);
            }
            if (this.encounter) {
                widgetField.attr("data-encounter-uuid", this.encounter?.uuid);
            }
            if (options?.orderUuid) {
                widgetField.attr("data-order-uuid", options.orderUuid);
            }
            if (initialObs) {
                widgetField.attr("data-obs-uuid", initialObs.uuid);
            }

            widget.append(jq("<div>").addClass("field-error"));
            this.obsWidgetFields.push(widgetField);
        }

        return widget;
    }

    // Methods to create standard form widgets

    /**
     * config;
     *   - id
     *   - name
     *   - options
     *   - initialValue
     */
    createSelectWidget(config) {
        const jq = this.jq;
        const widget = jq("<select>");
        if (config.id) {
            widget.attr("id", config.id);
        }
        if (config.name) {
            widget.attr("name", config.name);
        }
        widget.append(jq("<option>").attr("value", "").html(""));
        config?.options?.forEach((o) => {
            widget.append(jq("<option>").attr("value", o.value).html(o.display));
        });
        if (config.initialValue) {
            widget.val(config.initialValue);
        }
        return widget;
    }

    /**
     * config;
     *   - id
     *   - name
     *   - label
     *   - locale
     *   - useTime
     *   - minuteStep
     *   - initialValue
     *   https://github.com/diasks2/bootstrap-datetimepicker-1
     */
    createDatePickerWidget(config) {
        const jq = this.jq;
        const widget = jq("<span>").attr("id", config.id).addClass("date-widget");
        if (config.label) {
            const label = jq("<label>").attr("for", config.id + "-display").html(config.label ?? "");
            widget.append(label);
        }
        const dateWrapper = jq("<span>").attr("id", config.id + "-wrapper").addClass("date");
        const dateDisplayInput = jq("<input>").attr("type", "text").attr("id", config.id + "-display").attr("readonly", "true");
        const dateCalendarIcon = jq("<span>").addClass("add-on").append(jq("<i>").addClass("icon-calendar small"));
        const dateRemoveIcon = jq("<i>").addClass("icon-remove small");
        dateWrapper.append(dateDisplayInput).append(dateCalendarIcon).append(dateRemoveIcon);
        const hiddenInput = jq("<input>").attr("type", "hidden").attr("id", config.id + "-field");
        if (config.name) {
            hiddenInput.attr("name", config.name ?? "");
        }
        widget.append(dateWrapper).append(hiddenInput);

        const displayFormat = config.useTime ? "dd M yyyy hh:ii" : "dd M yyyy";
        const submitFormat = config.useTime ? "yyyy-mm-dd hh:ii:ss" : "yyyy-mm-dd";

        const initialValue = config.initialValue ? moment(config.initialValue) : null;
        dateDisplayInput.val(initialValue ? initialValue.format(config.useTime ? this.dateTimeFormat : this.dateFormat) : "");
        hiddenInput.val(initialValue ? initialValue.format() : "");

        const datePickerOptions = {
            autoclose: true,
            pickerPosition: "bottom-left",
            todayHighlight: true,
            minuteStep: config.minuteStep ?? 5,
            format: displayFormat,
            language: config.locale ?? "en",
            linkField: config.id + "-field",
            linkFormat: submitFormat,
        }
        if (!config.useTime) {
            datePickerOptions.minView = 2;
        }

        widget.find(".date").datetimepicker(datePickerOptions);

        const inputField = widget.find("input");
        widget.find(".icon-remove").on("click", () => {
            if (inputField.val() !== "") {
                inputField.val("").change();
            }
        });
        return widget;
    }

    constructEncounterPayload() {
        const encounter = {
            uuid: this.encounter?.uuid,
            encounterDatetime: this.encounter?.encounterDatetime,
            encounterType: this.encounter?.encounterType.uuid,
            location: this.encounter?.location.uuid,
            encounterProviders: this.encounter?.encounterProviders.map(ep => {
                return { provider: ep.provider.uuid, encounterRole: ep.encounterRole.uuid }
            }),
            obs: []
        }

        // Construct obs and obs groups in the encounter
        // TODO: Support multiple results per concept
        this.obsWidgetFields.forEach(obsWidgetFields => {
            const data = obsWidgetFields.data();
            const value = obsWidgetFields.val();
            const groupingConceptUuid = data.groupingConceptUuid;
            const conceptUuid = data.conceptUuid;
            const dataType = data.conceptDatatype;
            const obsUuid = data.obsUuid;
            const orderUuid = data.orderUuid;
            const formPath = data.formPath;
            const groupNamespaceAndPath = this.formName + "/" + groupingConceptUuid;
            const namespaceAndPath = this.formName + formPath;

            if (obsUuid || value) {
                const voidOnly = obsUuid && !value;
                const initialObs = this.initialObs.find(o => o.concept.uuid === conceptUuid);
                const valueToSet = voidOnly ? initialObs?.value : value;
                const obs = {
                    uuid: obsUuid,
                    order: orderUuid,
                    concept: conceptUuid,
                    formNamespaceAndPath: initialObs ? initialObs.formNamespaceAndPath : namespaceAndPath,
                    voided: voidOnly
                }
                if (dataType === "Coded") {
                    obs.valueCoded = valueToSet;
                }
                else if (dataType === "Text") {
                    obs.valueText = valueToSet;
                }
                else if (dataType === "Numeric") {
                    obs.valueNumeric = valueToSet;
                }
                else if (dataType === "Date" || dataType === "Datetime") {
                    obs.valueDatetime = valueToSet;
                }
                else {
                    obs.value = valueToSet;
                }

                if (groupingConceptUuid) {
                    let initialGroup = this.initialObs.find(o => o.concept.uuid === groupingConceptUuid);
                    let obsGroup = encounter.obs.find(o => o.concept === groupingConceptUuid);
                    if (!obsGroup) {
                        obsGroup = {
                            uuid: initialGroup?.uuid,
                            order: orderUuid,
                            concept: groupingConceptUuid,
                            formNamespaceAndPath: initialGroup ? initialGroup.formNamespaceAndPath : groupNamespaceAndPath,
                            groupMembers: []
                        };
                        encounter.obs.push(obsGroup);
                    }
                    obsGroup.groupMembers.push(obs);
                }
                else {
                    encounter.obs.push(obs);
                }
            }
        });

        // Void any obs groups that only have voided members
        encounter.obs.forEach(o => {
            if (o.groupMembers && o.groupMembers.length > 0) {
                const numNonVoided = o.groupMembers.filter(o => !o.voided).length;
                if (numNonVoided === 0) {
                    o.voided = true;
                }
            }
        });

        return encounter;
    }
}
