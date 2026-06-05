<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "pagingDataTable.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def now = new Date()
    def orderLabsPage = ui.pageLink("coreapps", "findpatient/findPatient", ["app": "pih.app.labs.ordering"])
    def visitLocationUuid = visitLocationForSessionLocation ? visitLocationForSessionLocation.uuid : ""
%>

<script type="text/javascript">
    const breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labOrderList")) }" , link: '${ui.pageLink("pihapps", "labs/labOrderList")}'}
    ];

    const visitLocationUuid = '${ visitLocationUuid }';

    const patientRep = "(uuid,display,person:(display),identifiers:(identifier,preferred,identifierType:(uuid,display,auditInfo:(dateCreated))))";
    const conceptRep = "(id,uuid,allowDecimal,display,displayStringForLab)";
    const orderRep = "id,uuid,display,orderNumber,dateActivated,scheduledDate,dateStopped,autoExpireDate,fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,patient:(uuid,display,person:(display),identifiers:(identifier,preferred,identifierType:(uuid,display,auditInfo:(dateCreated)))),concept:" + conceptRep

    const labOrderConfigRep = "(labTestOrderType:(uuid),availableLabTestsByCategory:(category:" + conceptRep + ",labTests:" + conceptRep + "),orderStatusOptions:(status,display),fulfillerStatusOptions:(status,display),orderFulfillmentStatusOptions:(status,display),testLocationQuestion:(uuid,datatype:(name),answers:(uuid,display)),specimenCollectionEncounterType:(uuid),specimenCollectionEncounterRole:(uuid),estimatedCollectionDateQuestion:(uuid,datatype:(name)),estimatedCollectionDateAnswer:(uuid),testOrderNumberQuestion:(uuid,datatype:(name)),labIdentifierConcept:(uuid,datatype:(name)),specimenReceivedDateQuestion:(uuid,datatype:(name)),resultsDateQuestion:(uuid,datatype:(name)),reasonTestNotPerformedQuestion:(uuid,datatype:(name),answers:(uuid,display)),collectResultComments)";
    const pihAppsConfigRep = "dateFormat,dateTimeFormat,primaryIdentifierType:(uuid),labOrderConfig:" + labOrderConfigRep;

    moment.locale(window.sessionContext?.locale ?? 'en');

    const pagingDataTable = new PagingDataTable(jq);
    const patientPagingDataTable = new PagingDataTable(jq);
    const patientOrderRep = "id,uuid,orderNumber,dateActivated,dateStopped,autoExpireDate,orderer:(display),fulfillerStatus,encounter:(id,uuid,display,encounterDatetime,location:(uuid,display)),fulfillerEncounter:(id,uuid,display,encounterDatetime),accessionNumber,urgency,action,patient:" + patientRep + ",concept:(id,uuid,allowDecimal,display,displayStringForLab)";
    const patientUtils = new PihAppsPatientUtils(jq);

    const viewSpecimenEncounter = function(encounterUuid) {
        const encounterRep = "id,uuid,patient:" + patientRep + ",encounterDatetime,encounterType:(uuid),location:(uuid,display),encounterProviders:(provider:(uuid,display),encounterRole:(uuid,display)),obs:(uuid,concept:(uuid,datatype:(name)),value,valueCoded:(uuid,display),valueNumeric,valueDatetime,valueText,comment,formNamespaceAndPath)";
        const rep = "encounter:(" + encounterRep + "),orders:(" + orderRep + ")";
        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + pihAppsConfigRep + ")", function(pihAppsConfig) {
            jq.get(openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders/" + encounterUuid + "?v=custom:(" + rep + ")", function (encAndOrders) {
                jq(".lab-emr-id").html(patientUtils.getPreferredIdentifier(encAndOrders.encounter.patient, pihAppsConfig.primaryIdentifierType?.uuid ?? ''));
                jq(".lab-patient-name").html(encAndOrders.encounter.patient.person.display);
                initializeSpecimenCollectionForm({
                    patientUuid: encAndOrders.encounter.patient.uuid,
                    orders: encAndOrders.orders,
                    encounter: encAndOrders.encounter,
                    pihAppsConfig: pihAppsConfig,
                    onSuccessFunction: () => { closeEncounterEdit(); pagingDataTable.updateTable(); }
                });
                openEncounterEdit();
            });
        });
    };

    const viewOrderNotPerformed = function(orderUuid) {
        const rep = orderRep + ",reasonOrderNotFulfilled:(uuid,concept:" + conceptRep + ",valueCoded:" + conceptRep + ")";
        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + pihAppsConfigRep + ")", function(pihAppsConfig) {
            jq.get(openmrsContextPath + "/ws/rest/v1/order/" + orderUuid + "?v=custom:(" + rep + ")", function (order) {
                jq(".lab-emr-id").html(patientUtils.getPreferredIdentifier(order.patient, pihAppsConfig.primaryIdentifierType?.uuid ?? ''));
                jq(".lab-patient-name").html(order.patient.person.display);
                initializeOrderNotFulfilledForm({
                    orders: [order],
                    reason: order.reasonOrderNotFulfilled,
                    pihAppsConfig: pihAppsConfig,
                    onSuccessFunction: () => { closeReasonNotPerformed(); pagingDataTable.updateTable(); }
                });
                openReasonNotPerformed();
            });
        });
    }

    const markNotPerformed = function(patient, orders) {
        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + pihAppsConfigRep + ")", function(pihAppsConfig) {
            jq(".lab-emr-id").html(patientUtils.getPreferredIdentifier(patient, pihAppsConfig.primaryIdentifierType?.uuid ?? ''));
            jq(".lab-patient-name").html(patient.person.display);
            initializeOrderNotFulfilledForm({
                orders: orders,
                pihAppsConfig: pihAppsConfig,
                onSuccessFunction: () => {
                    closeReasonNotPerformed();
                    if (jq("#group-by-patient-btn").hasClass("active")) {
                        patientPagingDataTable.updateTable();
                    } else {
                        pagingDataTable.updateTable();
                    }
                }
            });
            openReasonNotPerformed();
        });
    };

    const collectSpecimen = function(patient, orders, selectedOrderUuids) {
        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + pihAppsConfigRep + ")", function(pihAppsConfig) {
            jq(".lab-emr-id").html(patientUtils.getPreferredIdentifier(patient, pihAppsConfig.primaryIdentifierType?.uuid ?? ''));
            jq(".lab-patient-name").html(patient.person.display);
            initializeSpecimenCollectionForm({
                patientUuid: patient.uuid,
                orders: orders,
                selectedOrderUuids: selectedOrderUuids,
                encounter: null,
                pihAppsConfig: pihAppsConfig,
                onSuccessFunction: () => {
                    closeEncounterEdit();
                    if (jq("#group-by-patient-btn").hasClass("active")) {
                        patientPagingDataTable.updateTable();
                    } else {
                        pagingDataTable.updateTable();
                    }
                }
            });
            openEncounterEdit();
        });
    };

    const openSection = function(selector) {
        jq("#view-orders-section").hide();
        jq(selector).show();
    }

    const closeSection = function(selector) {
        jq(selector).hide();
        jq("#view-orders-section").show();
    }

    const openEncounterEdit = () => openSection("#edit-specimen-encounter-section");
    const closeEncounterEdit = () => closeSection("#edit-specimen-encounter-section");
    const openReasonNotPerformed = () => openSection("#edit-reason-not-performed-section");
    const closeReasonNotPerformed = () => closeSection("#edit-reason-not-performed-section");
    const openLabResults = () => openSection("#record-lab-results-section");
    const closeLabResults = () => closeSection("#record-lab-results-section");

    jq(document).ready(function() {

        // Read URL params for deep linking (e.g., Specimen Collection Queue)
        const urlParams = new URLSearchParams(window.location.search);
        const initialGrouping = urlParams.get('grouping');   // 'patient' or null
        const initialStatus = urlParams.get('status');        // e.g. 'AWAITING_FULFILLMENT' or null

        jq.get(openmrsContextPath + "/ws/rest/v1/pihapps/config?v=custom:(" + pihAppsConfigRep + ")", function(pihAppsConfig) {

            const primaryIdentifierType = pihAppsConfig.primaryIdentifierType?.uuid ?? '';
            const dateUtils = new PihAppsDateUtils(moment, pihAppsConfig.dateFormat, pihAppsConfig.dateTimeFormat);
            const orderFulfillmentStatusOptions = pihAppsConfig.labOrderConfig.orderFulfillmentStatusOptions;

            // Column functions
            const getEmrId = (order) => { return patientUtils.getPreferredIdentifier(order.patient, primaryIdentifierType); };
            const getPatientName = (order) => { return order.patient.person.display; }
            const getOrderDate = (order) => { return dateUtils.formatAsDateWithoutTime(order.dateActivated); };
            const getOrderLocation = (order) => {return order.encounter.location?.display; }
            const getOrderNumber = (order) => { return order.orderNumber; }
            const getAccessionNumber = (order) => { return order.accessionNumber; }

            const getLabTest = function(order) {
                return (order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color: red;"></i>' : '') + order.concept.displayStringForLab;
            }

            const getSpecimenDate = function(order) {
                const fulfillerEncounter = order.fulfillerEncounter;
                if (!fulfillerEncounter) {
                    return "";
                }
                const specimenDate = dateUtils.formatAsDateWithoutTime(fulfillerEncounter.encounterDatetime);
                return "<a href=\"javascript:viewSpecimenEncounter('" + fulfillerEncounter.uuid +  "')\">" + specimenDate + "</a>";
            }

            const getOrderFulfillmentStatus = (order) => {
                const statusDisplay = patientUtils.getOrderFulfillmentStatusOption(order, orderFulfillmentStatusOptions).display;
                if (order.fulfillerStatus === 'EXCEPTION') {
                    return "<a href=\"javascript:viewOrderNotPerformed('" + order.uuid +  "')\">" + statusDisplay + "</a>";
                }
                return statusDisplay;
            }

            const isExpiredOrder = (order) => {
                return !order.fulfillerStatus && !order.dateStopped &&
                    order.autoExpireDate && moment(order.autoExpireDate).isBefore(new Date());
            };

            const getActions = (order) => {
                const actions = jq("<span>").addClass("actions");
                if (order.fulfillerEncounter) {
                    const resultsTitle = order.fulfillerStatus === 'COMPLETED'
                        ? "${ ui.message('pihapps.editResults') }"
                        : "${ ui.message('pihapps.recordResults') }";
                    actions.append(
                        jq("<i>").addClass("fas fa-fw fa-clipboard-list lab-action-icon enter-results-action")
                            .attr({ "data-order-uuid": order.uuid, "title": resultsTitle })
                    );
                } else if (!isExpiredOrder(order)) {
                    actions.append(
                        jq("<i>").addClass("fas fa-fw fa-vial lab-action-icon collect-specimen-action")
                            .attr({ "data-order-uuid": order.uuid, "title": "${ ui.message('pihapps.collectSpecimen') }" })
                    );
                    actions.append(
                        jq("<i>").addClass("fas fa-fw fa-ban lab-action-icon mark-not-performed-action")
                            .attr({ "data-order-uuid": order.uuid, "title": "${ ui.message('pihapps.markNotPerformed') }" })
                    );
                }
                return actions.html();
            }

            const getFilterParameterValues = function() {
                return {
                    "orderType": pihAppsConfig.labOrderConfig.labTestOrderType?.uuid,
                    "orderLocation": jq("#orderLocation-filter").val() || visitLocationUuid,
                    "patient": jq("#patient-filter-field").val(),
                    "labTest": jq("#testConcept-filter").val(),
                    "activatedOnOrAfter": jq("#orderedFrom-filter-field").val(),
                    "activatedOnOrBefore": jq("#orderedTo-filter-field").val(),
                    "accessionNumber": jq("#lab-id-filter").val(),
                    "orderFulfillmentStatus": jq("#orderFulfillmentStatus-filter").val(),
                    "sortBy": "dateActivated-desc"  // TODO: Sorting by dateActivated desc does not seem right, but doing this to match existing labWorkflow, but shouldn't this order by urgency and asc?
                }
            }

            const orderTableUpdated = function() {
                jq(".enter-results-action").off("click").on("click", (event) => {
                    const orderUuid = jq(event.target).data().orderUuid;
                    const order = getOrderFromTable(orderUuid);
                    jq(".lab-emr-id").html(patientUtils.getPreferredIdentifier(order.patient, pihAppsConfig.primaryIdentifierType?.uuid ?? ''));
                    jq(".lab-patient-name").html(order.patient.person.display);
                    initializeLabResultsForm({
                        order: order,
                        pihAppsConfig: pihAppsConfig,
                        onSuccessFunction: () => {
                            closeLabResults();
                            pagingDataTable.updateTable();
                        },
                        onCancelFunction: () => {
                            closeLabResults();
                            closeReasonNotPerformed();
                        }
                    });
                    openLabResults();
                });
                jq(".collect-specimen-action").off("click").on("click", (event) => {
                    event.stopPropagation();
                    const orderUuid = jq(event.currentTarget).data().orderUuid;
                    const order = getOrderFromTable(orderUuid);
                    collectSpecimen(order.patient, [order], [order.uuid]);
                });
                jq(".mark-not-performed-action").off("click").on("click", (event) => {
                    event.stopPropagation();
                    const orderUuid = jq(event.currentTarget).data().orderUuid;
                    const order = getOrderFromTable(orderUuid);
                    markNotPerformed(order.patient, [order]);
                });
            }

            // ---- Patient grouping view ----

            const getAggregateStatus = (orders) => {
                if (!orders || orders.length === 0) return 'MIXED';
                const allExpired = orders.every(o => isExpiredOrder(o));
                if (allExpired) return 'EXPIRED';
                const allAwaiting = orders.every(o => !o.fulfillerEncounter && !isExpiredOrder(o));
                if (allAwaiting) return 'AWAITING';
                const allCompleted = orders.every(o => o.fulfillerStatus === 'COMPLETED');
                if (allCompleted) return 'COMPLETED';
                const allCollected = orders.every(o => !!o.fulfillerEncounter && o.fulfillerStatus !== 'COMPLETED');
                if (allCollected) return 'COLLECTED';
                return 'MIXED';
            };

            const getPatientColumn = (patientWithOrders) => {
                const emrId = patientUtils.getPreferredIdentifier(patientWithOrders.patient, primaryIdentifierType);
                return '<span data-patient-uuid="' + patientWithOrders.patient.uuid + '">' +
                    '<i class="fas fa-fw fa-caret-right expand-indicator mr-1"></i>' +
                    emrId + " — " + patientWithOrders.patient.person.display +
                    '</span>';
            };

            const getPatientOrdersSummary = (patientWithOrders) => {
                const count = patientWithOrders.orders.length;
                const labIds = [...new Set(patientWithOrders.orders.map(o => o.accessionNumber).filter(Boolean))];
                const labIdStr = labIds.length > 0 ? labIds.join(", ") : "";
                return count + " order" + (count !== 1 ? "s" : "") + (labIdStr ? " · " + labIdStr : "");
            };

            const getAggregateStatusBadge = (patientWithOrders) => {
                const status = getAggregateStatus(patientWithOrders.orders);
                const labels = {
                    'AWAITING':  { cls: 'badge-warning',   key: '${ui.message("pihapps.allAwaiting")}' },
                    'COLLECTED': { cls: 'badge-info',      key: '${ui.message("pihapps.allCollected")}' },
                    'COMPLETED': { cls: 'badge-success',   key: '${ui.message("pihapps.allCompleted")}' },
                    'EXPIRED':   { cls: 'badge-dark',      key: '${ui.message("pihapps.allExpired")}' },
                    'MIXED':     { cls: 'badge-secondary', key: '${ui.message("pihapps.mixedStatus")}' }
                };
                const label = labels[status] || labels['MIXED'];
                return '<span class="badge ' + label.cls + '">' + label.key + '</span>';
            };

            const getPatientGroupActions = (patientWithOrders) => {
                const actions = jq("<span>").addClass("patient-group-actions");
                const status = getAggregateStatus(patientWithOrders.orders);
                const patientUuid = patientWithOrders.patient.uuid;
                if (status === 'AWAITING') {
                    actions.append(
                        jq("<i>").addClass("fas fa-fw fa-vial lab-action-icon collect-specimen-group-action")
                            .attr({ "data-patient-uuid": patientUuid, "title": "${ ui.message('pihapps.collectSpecimen') }" })
                    );
                    actions.append(
                        jq("<i>").addClass("fas fa-fw fa-ban lab-action-icon mark-not-performed-group-action")
                            .attr({ "data-patient-uuid": patientUuid, "title": "${ ui.message('pihapps.markNotPerformed') }" })
                    );
                }
                // TODO: Print Results and Notify Patient require configuration before enabling.
                // Uncomment the block below once the downstream handlers are implemented.
                // else if (status === 'COMPLETED') {
                //     actions.append(
                //         jq("<i>").addClass("fas fa-fw fa-print lab-action-icon print-results-action mr-1")
                //             .attr({ "data-patient-uuid": patientUuid, "title": "${ ui.message('pihapps.printResults') }" })
                //     );
                //     actions.append(
                //         jq("<i>").addClass("fas fa-fw fa-bell lab-action-icon notify-patient-action")
                //             .attr({ "data-patient-uuid": patientUuid, "title": "${ ui.message('pihapps.notifyPatient') }" })
                //     );
                // }
                return actions.html();
            };

            const expandPatientRow = function(trElement, patientWithOrders) {
                const patientUuid = patientWithOrders.patient.uuid;
                const subRowClass = "patient-sub-row-" + patientUuid;
                const alreadyExpanded = jq("." + subRowClass).length > 0;
                jq("." + subRowClass).remove();
                if (!alreadyExpanded) {
                    jq(trElement).find(".expand-indicator").removeClass("fa-caret-right").addClass("fa-caret-down");
                    const subRows = [];
                    patientWithOrders.orders.forEach(order => {
                        const subRow = jq("<tr>").addClass("patient-sub-row " + subRowClass);
                        const urgencyIcon = order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color:red;"></i>' : '';
                        const metaLabel = (key) => '<span class="text-muted">' + key + ': </span>';
                        const cellHtml =
                            '<div class="patient-sub-row-details">' +
                            '<span class="psrd-test">' + urgencyIcon + order.concept.displayStringForLab + '</span>' +
                            '<span class="psrd-meta">' + metaLabel('${ ui.message("pihapps.orderDate") }') + dateUtils.formatAsDateWithoutTime(order.dateActivated) + '</span>' +
                            '<span class="psrd-meta">' + (order.encounter?.location?.display ? metaLabel('${ ui.message("pihapps.orderLocation") }') + order.encounter.location.display : '') + '</span>' +
                            '<span class="psrd-meta">' + (order.accessionNumber ? metaLabel('${ ui.message("pihapps.labId") }') + order.accessionNumber : '') + '</span>' +
                            '<span class="psrd-meta">' + metaLabel('${ ui.message("pihapps.orderNumber") }') + order.orderNumber + '</span>' +
                            '</div>';
                        subRow.append(jq("<td>").attr("colspan", "2").css("padding-left", "2em").html(cellHtml));
                        subRow.append(jq("<td>").html(getOrderFulfillmentStatus(order)));
                        const subActions = jq("<span>");
                        if (order.fulfillerEncounter) {
                            const subResultsTitle = order.fulfillerStatus === 'COMPLETED'
                                ? "${ ui.message('pihapps.editResults') }"
                                : "${ ui.message('pihapps.recordResults') }";
                            subActions.append(
                                jq("<i>").addClass("fas fa-fw fa-clipboard-list lab-action-icon enter-results-action")
                                    .attr({ "data-order-uuid": order.uuid, "title": subResultsTitle })
                                    .css("cursor", "pointer")
                            );
                        } else if (!isExpiredOrder(order)) {
                            subActions.append(
                                jq("<i>").addClass("fas fa-fw fa-vial lab-action-icon collect-specimen-action")
                                    .attr({ "data-order-uuid": order.uuid, "title": "${ ui.message('pihapps.collectSpecimen') }" })
                                    .css("cursor", "pointer")
                            );
                            subActions.append(
                                jq("<i>").addClass("fas fa-fw fa-ban lab-action-icon mark-not-performed-action")
                                    .attr({ "data-order-uuid": order.uuid, "title": "${ ui.message('pihapps.markNotPerformed') }" })
                                    .css("cursor", "pointer")
                            );
                        }
                        subRow.append(jq("<td>").append(subActions));
                        subRows.push(subRow);
                    });
                    // Insert all sub-rows after the patient row, maintaining order
                    let insertAfter = jq(trElement);
                    subRows.forEach(subRow => {
                        subRow.insertAfter(insertAfter);
                        insertAfter = subRow;
                    });

                    // Attach click handlers to newly inserted sub-rows
                    patientWithOrders.orders.forEach(order => {
                        const subRowClass2 = "patient-sub-row-" + patientUuid;
                        // Results entry
                        jq("." + subRowClass2 + " .enter-results-action[data-order-uuid='" + order.uuid + "']").on("click", (event) => {
                            event.stopPropagation();
                            jq(".lab-emr-id").html(patientUtils.getPreferredIdentifier(patientWithOrders.patient, pihAppsConfig.primaryIdentifierType?.uuid ?? ''));
                            jq(".lab-patient-name").html(patientWithOrders.patient.person.display);
                            initializeLabResultsForm({
                                order: order,
                                pihAppsConfig: pihAppsConfig,
                                onSuccessFunction: () => { closeLabResults(); patientPagingDataTable.updateTable(); },
                                onCancelFunction: () => { closeLabResults(); closeReasonNotPerformed(); }
                            });
                            openLabResults();
                        });
                        // Collect specimen
                        jq("." + subRowClass2 + " .collect-specimen-action[data-order-uuid='" + order.uuid + "']").on("click", (event) => {
                            event.stopPropagation();
                            collectSpecimen(patientWithOrders.patient, [order], [order.uuid]);
                        });
                        // Mark not performed (pre-collection)
                        jq("." + subRowClass2 + " .mark-not-performed-action[data-order-uuid='" + order.uuid + "']").on("click", (event) => {
                            event.stopPropagation();
                            markNotPerformed(patientWithOrders.patient, [order]);
                        });
                    });
                } else {
                    jq(trElement).find(".expand-indicator").removeClass("fa-caret-down").addClass("fa-caret-right");
                }
            };

            const patientTableUpdated = function() {
                jq("#patients-table tbody tr").each(function() {
                    const patientUuid = jq(this).find("[data-patient-uuid]").first().attr("data-patient-uuid");
                    if (patientUuid) {
                        jq(this).addClass("patient-group-row")
                            .attr("data-patient-uuid", patientUuid)
                            .css("cursor", "pointer");
                    }
                });

                // Collect Specimen at group level
                jq(".collect-specimen-group-action").off("click").on("click", (event) => {
                    event.stopPropagation();
                    const patientUuid = jq(event.currentTarget).data("patientUuid");
                    const patientWithOrders = patientPagingDataTable.getRowObjects().find(p => p.patient.uuid === patientUuid);
                    if (patientWithOrders) {
                        const awaitingOrders = patientWithOrders.orders.filter(o => !o.fulfillerEncounter && !isExpiredOrder(o));
                        collectSpecimen(patientWithOrders.patient, awaitingOrders, awaitingOrders.map(o => o.uuid));
                    }
                });

                // Mark Not Performed at group level (pre-collection)
                jq(".mark-not-performed-group-action").off("click").on("click", (event) => {
                    event.stopPropagation();
                    const patientUuid = jq(event.currentTarget).data("patientUuid");
                    const patientWithOrders = patientPagingDataTable.getRowObjects().find(p => p.patient.uuid === patientUuid);
                    if (patientWithOrders) {
                        const awaitingOrders = patientWithOrders.orders.filter(o => !o.fulfillerEncounter && !isExpiredOrder(o));
                        markNotPerformed(patientWithOrders.patient, awaitingOrders);
                    }
                });

                // TODO: Uncomment when Print Results and Notify Patient are implemented.
                // jq(".print-results-action").off("click").on("click", (event) => {
                //     event.stopPropagation();
                //     const patientUuid = jq(event.currentTarget).data("patientUuid");
                // });
                // jq(".notify-patient-action").off("click").on("click", (event) => {
                //     event.stopPropagation();
                //     const patientUuid = jq(event.currentTarget).data("patientUuid");
                // });

                // Expand/collapse on row click
                jq("#patients-table tbody tr.patient-group-row").off("click").on("click", function(event) {
                    if (jq(event.target).closest("button, a, i.lab-action-icon").length) return;
                    const patientUuid = jq(this).attr("data-patient-uuid");
                    const patientWithOrders = patientPagingDataTable.getRowObjects().find(p => p.patient.uuid === patientUuid);
                    if (patientWithOrders) {
                        expandPatientRow(this, patientWithOrders);
                    }
                });
            };

            pagingDataTable.initialize({
                tableSelector: "#orders-table",
                tableInfoSelector: "#orders-table-info-and-paging",
                endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/labOrder",
                representation: "custom:(id,uuid,display,orderNumber,dateActivated,scheduledDate,dateStopped,autoExpireDate,orderer:(display),fulfillerStatus,orderType:(id,uuid,display,name),encounter:(id,uuid,display,encounterDatetime,location:(uuid,display)),fulfillerEncounter:(id,uuid,display,encounterDatetime),careSetting:(uuid,name,careSettingType,display),accessionNumber,urgency,action,patient:" + patientRep + ",concept:" + conceptRep + ")",
                parameters: { ...getFilterParameterValues() },
                columnTransformFunctions: [
                    getEmrId, getPatientName, getOrderNumber, getOrderDate, getOrderLocation, getSpecimenDate, getAccessionNumber, getOrderFulfillmentStatus, getLabTest, getActions
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
                },
                tableUpdateCallback: () => {
                    orderTableUpdated();
                }
            });

            let patientTableInitialized = false;

            pihAppsConfig.labOrderConfig.availableLabTestsByCategory.forEach((labCategory) => {
                const optGroup = jq("<optGroup>").attr("label", labCategory.category.displayStringForLab);
                labCategory.labTests.forEach((labTest) => {
                    const labOpt = jq("<option>").attr("value", labTest.uuid).html(labTest.displayStringForLab);
                    optGroup.append(labOpt);
                });
                jq("#testConcept-filter").append(optGroup);
            });

            orderFulfillmentStatusOptions.forEach((statusOption) => {
                const option = jq("<option>").attr("value", statusOption.status).html(statusOption.display);
                jq("#orderFulfillmentStatus-filter").append(option);
            });

            if (visitLocationUuid) {
                const locationRep = "custom:(uuid,display,descendantLocations:(uuid,display,tags:(uuid,name)))";
                jq.get(openmrsContextPath + "/ws/rest/v1/location/" + visitLocationUuid + "?v=" + locationRep, function(visitLocation) {
                    jq("#orderLocation-filter").append(jq("<option>").attr("value", visitLocation.uuid).html(visitLocation.display));
                    (visitLocation.descendantLocations || [])
                        .filter(loc => loc.tags && loc.tags.some(t => t.name === "Login Location"))
                        .sort((a, b) => a.display.localeCompare(b.display))
                        .forEach(loc => jq("#orderLocation-filter").append(jq("<option>").attr("value", loc.uuid).html(visitLocation.display + " - " + loc.display)));
                    jq("#orderLocation-filter").val(visitLocationUuid);
                });
            }

            jq("#test-filter-form").find(":input").change(function () {
                const params = getFilterParameterValues();
                if (jq("#group-by-patient-btn").hasClass("active")) {
                    patientPagingDataTable.setParameters(params);
                    patientPagingDataTable.goToFirstPage();
                } else {
                    pagingDataTable.setParameters(params);
                    pagingDataTable.goToFirstPage();
                }
            });

            jq("#group-by-order-btn").on("click", function() {
                jq(this).blur();
                jq("#group-by-order-btn").addClass("active");
                jq("#group-by-patient-btn").removeClass("active");
                jq("#order-view-container").show();
                jq("#patient-view-container").hide();
                pagingDataTable.setParameters(getFilterParameterValues());
                pagingDataTable.goToFirstPage();
            });

            jq("#group-by-patient-btn").on("click", function() {
                jq(this).blur();
                jq("#group-by-patient-btn").addClass("active");
                jq("#group-by-order-btn").removeClass("active");
                jq("#order-view-container").hide();
                jq("#patient-view-container").show();
                if (!patientTableInitialized) {
                    patientPagingDataTable.initialize({
                        tableSelector: "#patients-table",
                        tableInfoSelector: "#patients-table-info-and-paging",
                        endpoint: openmrsContextPath + "/ws/rest/v1/pihapps/patientsWithOrders",
                        representation: "custom:patient:" + patientRep + ",orders:(" + patientOrderRep + ")",
                        parameters: { ...getFilterParameterValues() },
                        columnTransformFunctions: [
                            getPatientColumn, getPatientOrdersSummary, getAggregateStatusBadge, getPatientGroupActions
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
                        },
                        tableUpdateCallback: () => {
                            patientTableUpdated();
                        }
                    });
                    patientTableInitialized = true;
                } else {
                    patientPagingDataTable.setParameters(getFilterParameterValues());
                    patientPagingDataTable.goToFirstPage();
                }
            });

            // Apply deep-link grouping param
            if (initialGrouping === 'patient') {
                jq("#group-by-patient-btn").trigger("click");
            }

            jq("#specimen-encounter-section button.cancel").click((event) => {
                event.preventDefault();
                closeEncounterEdit();
            });
            jq("#reason-not-performed-section button.cancel").click((event) => {
                event.preventDefault();
                closeReasonNotPerformed();
            });

            const getOrderFromTable = function(orderUuid) {
                return pagingDataTable.getRowObjects().find((o) => o.uuid === orderUuid);
            }

            // Apply initial status from URL param, or fall back to default
            if (initialStatus) {
                jq("#orderFulfillmentStatus-filter").val(initialStatus).trigger("change");
            } else {
                jq("#orderFulfillmentStatus-filter").val("IN_FULFILLMENT").trigger("change");
            }

            // Add clear buttons to filters
            jq(".clearable-input-wrapper").find(".icon-remove").on("click", (event) => {
                const icon = jq(event.target);
                icon.siblings(".clearable-input").val("").change();
            })
        });
    });
</script>

<style>
#edit-specimen-encounter-section { display: none; }
    #edit-reason-not-performed-section { display: none; }
    #record-lab-results-section { display: none; }
    .select-buttons {
        padding-bottom: 10px;
    }
    .select-buttons button {
        margin-right: 5px; margin-left: 5px;
    }
    #patients-table tbody tr.patient-group-row:hover {
        background-color: #f0f4ff;
        cursor: pointer;
    }
    #patients-table tbody tr.patient-sub-row {
        font-size: 0.9em;
        background-color: #f9f9f9;
    }
    .patient-sub-row-details {
        display: flex;
        align-items: center;
    }
    .patient-sub-row-details .psrd-test {
        flex: 0 0 28%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        padding-right: 0.5rem;
    }
    .patient-sub-row-details .psrd-meta {
        flex: 0 0 18%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        padding-right: 0.5rem;
    }
    .lab-action-icon {
        font-size: 1.1em;
        cursor: pointer;
        padding: 0 4px;
        color: #495057;
    }
    .lab-action-icon:hover {
        color: #0056b3;
    }
    .lab-action-icon + .lab-action-icon {
        margin-left: 1.25rem;
    }
    .lab-action-icon.fa-ban {
        color: #adb5bd;
    }
    .lab-action-icon.fa-ban:hover {
        color: #dc3545;
    }
    .expand-indicator {
        color: #6c757d;
        font-size: 0.9em;
    }
    #grouping-toggle .btn.active {
        background-color: #6c757d;
        color: #fff;
        border-color: #6c757d;
    }
</style>

<div id="view-orders-section">
    <div class="row justify-content-between align-items-center mb-2">
        <div class="col-auto">
            <h3>${ ui.message("pihapps.labOrderList") }</h3>
        </div>
        <div class="col-auto d-flex align-items-center">
            <span class="mr-2 text-muted" style="font-size:.85em;">${ ui.message("pihapps.groupBy") }:</span>
            <div class="btn-group mr-3" id="grouping-toggle" role="group">
                <button type="button" class="btn btn-sm btn-outline-secondary active" id="group-by-order-btn">${ ui.message("pihapps.groupByOrder") }</button>
                <button type="button" class="btn btn-sm btn-outline-secondary" id="group-by-patient-btn">${ ui.message("pihapps.groupByPatient") }</button>
            </div>
            <a class="btn btn-sm btn-secondary" style="color:#fff;" href="${ orderLabsPage }">${ ui.message("pihapps.addLabOrders") }</a>
        </div>
    </div>

    <form method="get" id="test-filter-form">
        <div class="row justify-content-start align-items-end">
            <div class="col">
                ${ ui.includeFragment("pihapps", "field/datetimepicker", [
                        id: "orderedFrom-filter",
                        formFieldName: "orderedFrom",
                        label: "pihapps.orderedFrom",
                        classes: "form-control",
                        endDate: now,
                        useTime: false,
                        clearButton: true
                ])}
            </div>
            <div class="col">
                ${ ui.includeFragment("pihapps", "field/datetimepicker", [
                        id: "orderedTo-filter",
                        formFieldName: "orderedTo",
                        label: "pihapps.orderedTo",
                        classes: "form-control",
                        endDate: now,
                        useTime: false,
                        clearButton: true
                ])}
            </div>
            <div class="col">
                <label for="orderFulfillmentStatus-filter">${ ui.message("pihapps.orderStatus") }</label>
                <div class="clearable-input-wrapper">
                    <select id="orderFulfillmentStatus-filter" name="orderFulfillmentStatus" class="clearable-input"></select>
                    <i class="icon-remove small"></i>
                </div>
            </div>
            <div class="col">
                <label for="testConcept-filter">${ ui.message("pihapps.labTest") }</label>
                <div class="clearable-input-wrapper">
                    <select id="testConcept-filter" name="testConcept" class="clearable-input">
                        <option value=""></option>
                    </select>
                    <i class="icon-remove small"></i>
                </div>
            </div>
        </div>
        <div class="row justify-content-start align-items-end">
            <div class="col-md-6 col-sm-6">
                <label for="patient-filter-display">${ ui.message("pihapps.patient") }</label>
                ${ ui.includeFragment("pihapps", "field/patient", [ id: "patient-filter", formFieldName: "patient" ]) }
            </div>
            <div class="col">
                <label for="lab-id-filter">${ ui.message("pihapps.labId") }:</label>
                <div class="clearable-input-wrapper">
                    <input id="lab-id-filter" class="clearable-input" type="text" name="labId" value=""/>
                    <i class="icon-remove small"></i>
                </div>
            </div>
            <div class="col">
                <label for="orderLocation-filter">${ ui.message("pihapps.orderLocation") }</label>
                <select id="orderLocation-filter" name="orderLocation" class="form-control"></select>
            </div>
        </div>
    </form>
    <div id="order-view-container">
    <table id="orders-table">
        <thead>
            <tr>
                <th>${ ui.message("pihapps.emrId") }</th>
                <th>${ ui.message("pihapps.name") }</th>
                <th>${ ui.message("pihapps.orderNumber") }</th>
                <th>${ ui.message("pihapps.orderDate") }</th>
                <th>${ ui.message("pihapps.orderLocation") }</th>
                <th>${ ui.message("pihapps.specimenDate") }</th>
                <th>${ ui.message("pihapps.labId") }</th>
                <th>${ ui.message("pihapps.orderFulfillmentStatus") }</th>
                <th>${ ui.message("pihapps.labTest") }</th>
                <th>${ ui.message("pihapps.actions") }</th>
            </tr>
        </thead>
        <tbody></tbody>
    </table>
    <div id="orders-table-info-and-paging" style="font-size: .9em">
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
    <div id="patient-view-container" style="display:none;">
    <table id="patients-table">
        <thead>
            <tr>
                <th>${ ui.message("pihapps.patient") }</th>
                <th>${ ui.message("pihapps.orders") }</th>
                <th>${ ui.message("pihapps.orderFulfillmentStatus") }</th>
                <th>${ ui.message("pihapps.actions") }</th>
            </tr>
        </thead>
        <tbody></tbody>
    </table>
    <div id="patients-table-info-and-paging" style="font-size: .9em">
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
</div>

<div id="edit-specimen-encounter-section">
    <div class="row justify-content-between">
        <div class="col-6">
            <h3>
                ${ ui.message("pihapps.specimenCollectionDetails") } -
                <span class="lab-patient-name"></span>
                (<span class="lab-emr-id"></span>)
            </h3>
        </div>
    </div>
    ${ ui.includeFragment("pihapps", "labs/specimenCollectionEncounter", ["id": "specimen-encounter-section"])}
</div>

<div id="edit-reason-not-performed-section">
    <div class="row justify-content-between">
        <div class="col-6">
            <h3>
                ${ ui.message("pihapps.removeSelectedOrders") } -
                <span class="lab-patient-name"></span>
                (<span class="lab-emr-id"></span>)
            </h3>
        </div>
    </div>
    ${ ui.includeFragment("pihapps", "labs/recordOrderNotFulfilled", ["id": "reason-not-performed-section"])}
</div>

<div id="record-lab-results-section">
    <div class="row justify-content-between">
        <div class="col-6">
            <h3>
                ${ ui.message("pihapps.recordLabResults") } -
                <span class="lab-patient-name"></span>
                (<span class="lab-emr-id"></span>)
            </h3>
        </div>
    </div>
    ${ ui.includeFragment("pihapps", "labs/recordLabResults", ["id": "lab-results-section"])}
</div>