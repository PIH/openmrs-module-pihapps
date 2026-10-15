/**
 * Opens the specimen collection form (the labs/specimenCollectionEncounter fragment) to collect a new specimen or to
 * edit an existing specimen collection.  Shared by the pages that embed that fragment, which need to supply:
 *   openForm / closeForm:  functions that show/hide the page section that contains the fragment
 *   loadErrorMessage:      the (localized) message to show if an existing specimen collection cannot be loaded
 */
class PihAppsSpecimenCollection {

    constructor(options) {
        this.openForm = options.openForm;
        this.closeForm = options.closeForm;
        this.loadErrorMessage = options.loadErrorMessage;
    }

    /**
     * The pihapps config fields that the specimen collection form needs
     */
    static getConfigRep() {
        return "testLocationQuestion:(uuid,datatype:(name),answers:(uuid,display))," +
            "specimenCollectionEncounterType:(uuid)," +
            "specimenCollectionEncounterRole:(uuid)," +
            "estimatedCollectionDateQuestion:(uuid,datatype:(name))," +
            "estimatedCollectionDateAnswer:(uuid)," +
            "testOrderNumberQuestion:(uuid,datatype:(name))," +
            "labIdentifierConcept:(uuid,datatype:(name))," +
            "specimenReceivedDateQuestion:(uuid,datatype:(name))";
    }

    /**
     * An order can have its specimen collected if it has no specimen collection encounter yet and is not canceled,
     * expired, completed, or marked as not performed.  Note that this includes orders with a fulfiller status that says
     * fulfillment has started (eg. IN_PROGRESS) even though there is no encounter, which labOrderList.gsp calls ORPHANED.
     */
    static isCollectable(order) {
        if (order.dateStopped || order.fulfillerEncounter) {
            return false;
        }
        if (order.fulfillerStatus === 'COMPLETED' || order.fulfillerStatus === 'EXCEPTION') {
            return false;
        }
        return !(order.autoExpireDate && moment(order.autoExpireDate).isBefore(new Date()));
    }

    /**
     * Collect a new specimen for the given orders
     * options: { patientUuid, orders, selectedOrderUuids, pihAppsConfig, onSuccess }
     */
    collect(options) {
        showSpecimenCollectionLoading();
        this.openForm();
        initializeSpecimenCollectionForm({
            patientUuid: options.patientUuid,
            orders: options.orders,
            selectedOrderUuids: options.selectedOrderUuids,
            encounter: null,
            pihAppsConfig: options.pihAppsConfig,
            onSuccessFunction: () => this.onSuccess(options)
        });
    }

    /**
     * Edit an existing specimen collection encounter
     * options: { encounterUuid, orderRep, patientRep, pihAppsConfig, onSuccess }
     */
    edit(options) {
        const orderRep = options.orderRep ?? "id,uuid,display,orderNumber,dateActivated,fulfillerStatus,urgency,action,concept:(id,uuid,allowDecimal,display,displayStringForLab)";
        const encounterRep = "id,uuid,patient:" + (options.patientRep ?? "(uuid)") + ",encounterDatetime,encounterType:(uuid),location:(uuid,display),encounterProviders:(provider:(uuid,display),encounterRole:(uuid,display)),obs:(uuid,concept:(uuid,datatype:(name)),value,valueCoded:(uuid,display),valueNumeric,valueDatetime,valueText,comment,formNamespaceAndPath)";
        const rep = "encounter:(" + encounterRep + "),orders:(" + orderRep + ")";
        showSpecimenCollectionLoading();
        this.openForm();
        jq.get(openmrsContextPath + "/ws/rest/v1/encounterFulfillingOrders/" + options.encounterUuid + "?v=custom:(" + rep + ")", (encAndOrders) => {
            initializeSpecimenCollectionForm({
                patientUuid: encAndOrders.encounter.patient.uuid,
                orders: encAndOrders.orders,
                selectedOrderUuids: encAndOrders.orders.map(o => o.uuid),
                encounter: encAndOrders.encounter,
                pihAppsConfig: options.pihAppsConfig,
                onSuccessFunction: () => this.onSuccess(options)
            });
        }).fail((xhr, status, error) => {
            // Without this the user would be left on the loading spinner with no way back to the orders list
            this.closeForm();
            emr.errorMessage(this.loadErrorMessage + ": " + (xhr.responseJSON?.error?.message ?? error));
        });
    }

    onSuccess(options) {
        this.closeForm();
        options.onSuccess();
    }
}
