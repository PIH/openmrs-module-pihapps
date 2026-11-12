/**
 * Actions for the lab order list's patientGroupActions extension point, referenced by an extension's jsFunction.
 * Each opens labResultsByLabId.page for the patient's orders. Validating is built into the lab order list.
 */
function pihappsLabOpenResultEntry(patientWithOrders, mode) {
    window.location.href = openmrsContextPath + "/pihapps/labs/labResultsByLabId.page?patient="
        + encodeURIComponent(patientWithOrders.patient.uuid) + "&mode=" + mode;
}

function pihappsLabEnterResults(patientWithOrders) {
    pihappsLabOpenResultEntry(patientWithOrders, "enter");
}
