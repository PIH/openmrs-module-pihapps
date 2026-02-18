/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p>
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.pihapps;

import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Order;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.module.pihapps.orders.PatientWithOrdersSearchResult;

import java.util.List;

public interface PihAppsService extends OpenmrsService {

	void updateVisitAndLoginLocations(List<Location> visitLocations, List<Location> loginLocations);

	OrderSearchResult getOrders(OrderSearchCriteria searchCriteria);

	PatientWithOrdersSearchResult getPatientsWithOrders(OrderSearchCriteria searchCriteria);

	EncounterFulfillingOrders saveEncounterFulfillingOrders(EncounterFulfillingOrders encounterFulfillingOrders);

	EncounterFulfillingOrders getEncounterFulfillingOrders(String encounterUuid);

	void markOrdersAsNotFulfilled(List<Order> orders, Concept reason);
}
