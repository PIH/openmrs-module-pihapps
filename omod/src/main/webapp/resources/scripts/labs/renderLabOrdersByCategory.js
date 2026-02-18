/*
    The goal of this function is to replace the standard orderWidget initialization javascript with one that
    renders lab orders as checkboxes and supports the same capabilities as the standalone lab orders page.  This supports 2 actions:
    1. If a lab has previously been ordered in the given encounter, it starts out checked.  Unchecking and saving will discontinue/void that order.
    2. If a lab has not been previously ordered in the given encounter, it starts out as non-checked.  Checking and saving will add a new order.
 */
function renderLabOrdersByCategory(config) {
    const $widgetField = jq('#' + config.fieldName);
    const $orderSection = $widgetField.find(".orderwidget-order-section");
    const $editTemplateSection = jq('#' + config.fieldName + "_template");
    const $viewTemplateSection = jq('#' + config.fieldName + "_view_template");
    const $templateSection = $editTemplateSection.length > 0 ? $editTemplateSection : $viewTemplateSection;
    const isViewMode = (config.mode === 'VIEW');

    // Add class for css styling
    $orderSection.addClass("lab-orders-by-category");

    // Determine which fields need to be collected, by examining both the template or default configuration with multiple options
    const labOrderFields = Object.keys(config.widgets);

    // Iterate over each field in the template and determine if it should be displayed or not
    const templateSections = $templateSection.find(".order-field-widget");
    const fieldSections = [];
    templateSections.each(function () {
        const fieldName = labOrderFields.filter((field) => jq(this).hasClass("order-" + field)).at(0);
        const fieldWidgetSection = $templateSection.find(".order-field.order-" + fieldName);
        const isInFormTemplate = fieldWidgetSection.parents(".non-template-field").length === 0;
        fieldSections.push({fieldName, fieldWidgetSection, isInFormTemplate})
    });

    // Get any existing orders by concept
    const previousOrders = new Map();
    config.history.forEach(function(order) {
        if (order.encounterId === config.encounterId) {
            previousOrders.set(order.concept.value, order);
        }
    });

    config.labTestCategories.forEach(function(category) {

        // Create section for each category, with the category name, and the category tests
        const $labCategorySection = jq(document.createElement("div")).attr("id", "lab-category-" + category.conceptId).addClass("lab-category");
        const $labCategoryExpander = jq(document.createElement("i")).attr("id", "lab-category-expander-" + category.conceptId).addClass("lab-category-expander");
        const $labCategoryNameElement = jq(document.createElement("span")).addClass("lab-category-name").html(category.displayName);
        const $labCategoryTestsSection = jq(document.createElement("div")).attr("id", "lab-category-tests-" + category.conceptId).addClass("lab-category-tests");
        $labCategorySection.append($labCategoryExpander).append($labCategoryNameElement).append($labCategoryTestsSection);
        $orderSection.append($labCategorySection);

        // Set up toggle for category to show/hide
        $labCategoryExpander.addClass("icon-angle-right");
        $labCategoryTestsSection.css("display", "none");

        const expandLabCategory = function () {
            $labCategoryExpander.removeClass("icon-angle-right");
            $labCategoryExpander.addClass("icon-angle-down");
            $labCategoryTestsSection.css("display", "block");
        }

        const collapseLabCategory = function () {
            $labCategoryExpander.removeClass("icon-angle-down");
            $labCategoryExpander.addClass("icon-angle-right");
            $labCategoryTestsSection.css("display", "none");
        }

        $labCategoryExpander.click(function () {
            if ($labCategoryExpander.hasClass("icon-angle-right")) {
                expandLabCategory();
            }
            else {
                collapseLabCategory();
            }
        });

        // Organize the tests to render based on whether they are in panels
        const configuredTests = new Map();
        category.labTests.forEach(test => {
            configuredTests.set(test.conceptId, test);
        });

        // Render the tests the given category
        configuredTests.forEach(function(labTest) {

            const testIsPanel = labTest.testsInPanel && labTest.testsInPanel.length > 0;
            const previousOrder = previousOrders.get(labTest.conceptId);

            if (previousOrder) {
                expandLabCategory();
            }

            const idSuffix = '_' + labTest.conceptId;

            const $labSection = jq(document.createElement("div")).attr("id", "lab" + idSuffix).addClass("lab-test-section");
            if (testIsPanel) {
                $labSection.addClass("lab-panel-section");
            }
            $labCategoryTestsSection.append($labSection);

            // Create the tooltip section
            const $toolTipSection = jq(document.createElement("span")).addClass("panel-tool-tip-section");
            if (testIsPanel) {
                const $toolTipButton = jq(document.createElement("i")).addClass("icon-info-sign").addClass("tooltip");
                const $toolTipText = jq(document.createElement("span")).addClass("tooltip-text");
                const $titleText = jq(document.createElement("span")).html(config.translations.testsIncludedInThisPanel);
                const $hideButton = jq(document.createElement("span")).html("X").addClass("hide-tooltip");
                const $toolTipTitle = jq(document.createElement("p")).append($titleText).append($hideButton);
                const $toolTipTests = jq(document.createElement("div"));
                labTest.testsInPanel.forEach(function(test) {
                    $toolTipTests.append(jq(document.createElement("span")).html(test.displayName));
                });
                $toolTipText.append($toolTipTitle);
                $toolTipText.append($toolTipTests);
                $toolTipButton.append($toolTipText);
                $toolTipSection.append($toolTipButton);

                $toolTipButton.click(function () {
                    if ($toolTipText.css("visibility") === "hidden") {
                        $toolTipText.css("visibility", "visible").css("opacity", 1);
                    }
                    else {
                        $toolTipText.css("visibility", "hidden").css("opacity", 0);
                    }
                });
            }

            // Set up action inputs.  These are only added in non-view mode
            const actionField = config.widgets.action + idSuffix;
            const $actionInput = jq(document.createElement("input")).prop({id: actionField, name: actionField, value: '', type: 'hidden'});

            // Handle the test concept, action, and previous order fields first
            if (isViewMode) {
                const $labValueSection = jq(document.createElement("span"));
                if (previousOrder) {
                    $labValueSection.addClass("value").html("[X]&nbsp;" + labTest.displayName);
                } else {
                    $labValueSection.addClass("emptyValue").html("[&nbsp;&nbsp;]&nbsp;" + labTest.displayName);
                }
                $labSection.append($labValueSection);
                $labSection.append($toolTipSection);
            }
            else {
                const toggleField = "order_toggle"  + idSuffix;
                const toggleInput = jq(document.createElement("input")).prop({id: toggleField, name: toggleField, value: '', type: 'checkbox'}).addClass("order-toggle");
                $labSection.append(toggleInput);
                const readOnlyToggle = jq(document.createElement("span")).addClass("order-toggle-readonly");
                $labSection.append(toggleInput);
                $labSection.append(readOnlyToggle);
                $labSection.append($actionInput);

                const conceptField = config.widgets.concept + idSuffix;
                const conceptInput = jq(document.createElement("input")).prop({id: conceptField, name: conceptField, value: labTest.conceptId, type: 'hidden'});
                $labSection.append(conceptInput).append(" " + labTest.displayName);
                $labSection.append($toolTipSection);

                const previousOrderField = config.widgets.previousOrder + idSuffix;
                const previousOrderInput = jq(document.createElement("input")).prop({id: previousOrderField, name: previousOrderField, type: 'hidden'});
                $labSection.append(previousOrderInput);

                // Set up the toggle checkbox to set the order action to either NEW, DISCONTINUE, or null
                jq($actionInput).val('');
                if (previousOrder) {
                    jq(previousOrderInput).val(previousOrder ? previousOrder.orderId : '');
                    jq(toggleInput).prop("checked", true);
                    jq($actionInput).val("");
                }

                jq(toggleInput).click(function() {
                    if(jq(toggleInput).is(':checked')) {
                        jq($actionInput).val(previousOrder ? "REVISE" : "NEW");
                        $labSection.find(".lab-fields").show();
                        if (testIsPanel) {
                            toggleTestsInPanels();
                        }
                    }
                    else {
                        jq($actionInput).val(previousOrder ? "DISCONTINUE" : "");
                        $labSection.find(".lab-fields").hide();
                        if (testIsPanel) {
                            toggleTestsInPanels();
                        }
                    }
                });
            }

            // Next, handle the fields associated with each test that we want to collect

            const $labFieldsSection = jq(document.createElement("span")).attr("id", "lab-fields" + idSuffix).addClass("lab-fields");
            $labSection.append($labFieldsSection);
            if (previousOrder) {
                $labFieldsSection.show();
            }
            else {
                $labFieldsSection.hide();
            }

            const excludedFields = ["action", "concept", "previousOrder", "dateActivated"];

            fieldSections.forEach(function(fieldSection) {
                const field = fieldSection.fieldName;
                if (!excludedFields.includes(field)) {
                    const $clonedFieldSection = jq(fieldSection.fieldWidgetSection).clone(true, true);

                    $clonedFieldSection.find("[id]").add($clonedFieldSection).each(function () {
                        if (this.id) {
                            this.id = this.id + idSuffix;
                        }
                    });
                    $clonedFieldSection.find("[name]").add($clonedFieldSection).each(function () {
                        if (this.name) {
                            this.name = this.name + idSuffix;
                        }
                    });

                    $clonedFieldSection.find(":input").addClass("lab-order-field-input");

                    // We have special handling for orderReason, so do this first
                    if (field === "orderReason") {
                        if (isViewMode) {
                            if (previousOrder && previousOrder.orderReason) {
                                let display = previousOrder.orderReason.display;
                                if (labTest.reasons) {
                                    labTest.reasons.forEach((reason) => {
                                        if (reason.conceptId === previousOrder.orderReason.value) {
                                            display = reason.displayName;
                                        }
                                    })
                                }
                                $clonedFieldSection.find(".order-field-widget").html(display).addClass("value");
                                if (!display) {
                                    $clonedFieldSection.hide();
                                }
                                $labFieldsSection.append($clonedFieldSection);
                            }
                        }
                        else {
                            if (labTest.reasons && labTest.reasons.length > 0) {
                                const $orderReasonSelect = $clonedFieldSection.find("select");
                                labTest.reasons.forEach(function (reason) {
                                    $orderReasonSelect.append(jq(document.createElement("option")).attr("value", reason.conceptId).html(reason.displayName));
                                });
                                $labFieldsSection.append($clonedFieldSection);
                            } else {
                                $clonedFieldSection.hide();
                            }
                        }
                    }
                    else {
                        const existingValueDisplay = previousOrder && previousOrder[field] ? previousOrder[field].display : "";
                        const hasExistingValue = existingValueDisplay !== "";
                        const fieldOptions = config.orderPropertyOptions[field] || [];
                        const numConfiguredOptions = fieldOptions.length;
                        const includeInEditMode = fieldSection.isInFormTemplate || numConfiguredOptions > 1;
                        const includeInViewMode = fieldOptions ? (hasExistingValue && numConfiguredOptions > 1) : hasExistingValue;
                        const includeField = isViewMode ? includeInViewMode : includeInEditMode;
                        if (!includeField) {
                            $clonedFieldSection.hide();
                        }
                        if (isViewMode) {
                            $clonedFieldSection.find(".order-field-widget").html(existingValueDisplay).addClass("value");
                            $labFieldsSection.append($clonedFieldSection);
                        }
                        $labFieldsSection.append($clonedFieldSection);
                    }
                }
            });

            if (previousOrder) {
                orderWidget.populateOrderForm(config, $labFieldsSection, previousOrder);

                // If there is a previous order, watch for any changes to fields and set the action to REVISE if so
                $labFieldsSection.find(":input").change(function () {
                    $actionInput.val('REVISE');
                });
            }
        });

        // Function that runs whenever a category is rendered or panels within it are toggled
        const toggleTestsInPanels = function() {
            const selectedWithinPanels = new Set();

            // First iterate over any panels, and track any tests that are part of selected panels
            category.labTests.forEach(function(test) {
                const panelSelected = jq("#order_toggle_" + test.conceptId).prop("checked");
                if (test.testsInPanel && panelSelected) {
                    test.testsInPanel.forEach(function (test) {
                        selectedWithinPanels.add(test.conceptId);
                    });
                }
            });

            // Next, iterate over non-panels, and render tests within those panels
            category.labTests.forEach(function(test) {
                if (!test.testsInPanel || test.testsInPanel.size === 0) {
                    const $checkbox = jq("#order_toggle_" + test.conceptId);
                    const $readOnlySection = $checkbox.siblings(".order-toggle-readonly");
                    const testAlreadySelected = $checkbox.prop("checked");
                    // If the test is already selected, or if is not in a selected panel, allow it to be edited
                    if (testAlreadySelected || !selectedWithinPanels.has(test.conceptId)) {
                        // Enable the checkboxes within the panel for selection
                        $readOnlySection.css("display", "none");
                        $checkbox.css("display", "inline");
                    } else {
                        $checkbox.css("display", "none");
                        $readOnlySection.html("[ X ]").css("display", "inline");
                    }
                }
            });
        }
        if (!isViewMode) {
            toggleTestsInPanels();
        }
    });
}
