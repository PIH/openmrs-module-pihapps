/**
 * Adds javascript functions useful for working with patient data
 */
class PihAppsPatientUtils {

    constructor(jq) {
        this.jq = jq;
    }

    getPreferredIdentifier(patient, patientIdentifierTypeUuid) {
        const sortedIdentifiers = patient.identifiers.sort(function(a, b) {
            const aPrimary = a.identifierType.uuid = patientIdentifierTypeUuid;
            const bPrimary = b.identifierType.uuid = patientIdentifierTypeUuid;
            if (aPrimary && !bPrimary) {
                return -1;
            }
            if (!aPrimary && bPrimary) {
                return 1;
            }
            if (a.preferred && !b.preferred) {
                return -1;
            }
            if (!a.preferred && b.preferred) {
                return 1;
            }
            const aCreated = new Date(a.identifierType.auditInfo.dateCreated).getTime();
            const bCreated = new Date(b.identifierType.auditInfo.dateCreated).getTime();
            return aCreated - bCreated;
        });
        return sortedIdentifiers && sortedIdentifiers.length > 0 ? sortedIdentifiers[0].identifier : "";
    };

    getOrderStatusOption(order, orderStatusOptions) {
        if (order.dateStopped) {
            return orderStatusOptions.filter((option) => option.status === 'STOPPED')[0];
        }
        if (order.autoExpireDate && moment(order.autoExpireDate).isBefore(new Date())) {
            return orderStatusOptions.filter((option) => option.status === 'EXPIRED')[0];
        }
        return orderStatusOptions.filter((option) => option.status === 'ACTIVE')[0];
    }

    getFulfillerStatusOption(order, fulfillerStatusOptions) {
        return fulfillerStatusOptions.filter((option) => option.status === (order.fulfillerStatus ?? "none"))[0];
    }
}
