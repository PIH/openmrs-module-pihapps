package org.openmrs.module.pihapps.service;

import org.openmrs.Patient;


import java.util.List;

public interface LabPrescriptionService {
     // Returns a list of lab prescriptions for the given patient.
     List<String> getLabPrescriptions(Patient patient);
}
