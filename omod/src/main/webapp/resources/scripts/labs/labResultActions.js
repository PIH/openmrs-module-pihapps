/**
 * Actions for the lab order list's patientGroupActions extension point, referenced by an extension's jsFunction.
 * Each opens the lab result entry page for the patient's orders.
 */
function pihappsLabOpenResultEntry(patientWithOrders, mode) {
    window.location.href = openmrsContextPath + "/pihapps/labs/labResultEntry.page?patient="
        + encodeURIComponent(patientWithOrders.patient.uuid) + "&mode=" + mode;
}

function pihappsLabEnterResults(patientWithOrders) {
    pihappsLabOpenResultEntry(patientWithOrders, "enter");
}

function pihappsLabValidateResults(patientWithOrders) {
    pihappsLabOpenResultEntry(patientWithOrders, "validate");
}
