package org.openmrs.module.pihapps.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.HibernateUtil;
import org.openmrs.module.pihapps.service.LabPrescriptionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.pihapps.constant.AppConstant.LAB_TEST_ORDERED_MAPPING;
import static org.openmrs.module.pihapps.constant.AppConstant.LAB_TEST_ORDERED_MAPPING_SOURCE;

@Service
@Slf4j
public class LabPrescriptionServiceImpl  implements LabPrescriptionService {
   private final OrderService orderService;
   private final ConceptService conceptService;
   private final ObsService obsService;

   public LabPrescriptionServiceImpl(
           @Qualifier("orderService") OrderService orderService,
           @Qualifier("conceptService") ConceptService conceptService,
           @Qualifier("obsService") ObsService obsService  ) {
           this.orderService = orderService;
           this.conceptService = conceptService;
           this.obsService = obsService;
   }

    @Override
    public List<String> getLabPrescriptions(Patient patient) {

        // This list is to store the lab exam name;
        List<String> labTests = new ArrayList<>();
        Concept concept = conceptService.getConceptByMapping(LAB_TEST_ORDERED_MAPPING, LAB_TEST_ORDERED_MAPPING_SOURCE);

        if (concept == null) {
            return Collections.emptyList();
        }

        // Get obs
        List<Obs> observations = obsService.getObservationsByPersonAndConcept(patient, concept);
        observations.sort(Comparator.comparing(Obs::getObsDatetime).reversed());
        Locale userLocale = Context.getLocale();


        // Get lab test names and store in list
        for (Obs obs : observations) {
            if (obs.getValueCoded() != null) {
                labTests.add(obs.getValueCoded().getName(userLocale).getName());
            }
        }

        // Get RECEIVED orders
        List<TestOrder> labOrders = getLabOrders(patient);
        Set<String> receivedOrderNames = labOrders.stream()
                .map(order -> order.getConcept().getName(userLocale).getName())
                .collect(Collectors.toSet());


        // Filter out names that already exist in received orders
        List<String> filteredLabTests = labTests.stream()
                .filter(name -> !receivedOrderNames.contains(name))
                .collect(Collectors.toList());

        // If all lab prescriptions are already received, return empty list
        return filteredLabTests.isEmpty() ? Collections.emptyList() : filteredLabTests;
    }

   // This method is to get all RECEIVED orders for a patient.
    private  List<TestOrder> getLabOrders(Patient patient) {
        List<TestOrder> labOrders = new ArrayList<>();
        for (Order order : orderService.getAllOrdersByPatient(patient)) {
            order = HibernateUtil.getRealObjectFromProxy(order);
            if (order instanceof TestOrder && BooleanUtils.isNotTrue(order.getVoided())) {
                labOrders.add((TestOrder) order);
            }
        }
        return labOrders;
    }


}
