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
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.pihapps.encounter.EncounterSearchCriteria;
import org.openmrs.module.pihapps.encounter.EncounterSearchResult;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchResult;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.module.pihapps.orders.PatientWithOrdersSearchResult;
import org.openmrs.util.PrivilegeConstants;

import java.util.List;
import java.util.Map;

public interface PihAppsService extends OpenmrsService {

	void updateVisitAndLoginLocations(List<Location> visitLocations, List<Location> loginLocations);

	OrderSearchResult getOrders(OrderSearchCriteria searchCriteria);

	PatientWithOrdersSearchResult getPatientsWithOrders(OrderSearchCriteria searchCriteria);

	EncounterFulfillingOrders saveEncounterFulfillingOrders(EncounterFulfillingOrders encounterFulfillingOrders);

	EncounterFulfillingOrders getEncounterFulfillingOrders(String encounterUuid);

	Encounter getFulfillerEncounterForOrder(Order order);

	Map<Order, Encounter> getFulfillerEncountersForOrders(List<Order> orders);

	Obs getReasonOrderNotFulfilled(Order order);

	void markOrdersAsNotFulfilled(List<Order> orders, Concept reason);

	void revertOrdersToOrdered(List<Order> orders);

	ObsSearchResult getObs(ObsSearchCriteria searchCriteria);

	/**
	 * Observations whose audit trail names the given user, most recent action first. Voided
	 * observations are included, since they are the whole point of a voidedBy search and are what
	 * an auditor most wants to see in a createdBy one.
	 *
	 * <p>The search is described by {@code createdBy}, {@code voidedBy} and the audit date bounds
	 * on {@link ObsSearchCriteria}; ordering defaults to the audit action the search named. At
	 * least one of the two users is required.
	 *
	 * @param searchCriteria what to search for, how to page it and how to sort it
	 * @return the matching observations and how many there are in total
	 * @throws org.openmrs.api.APIException if neither user is given
	 */
	@Authorized(PrivilegeConstants.GET_OBS)
	ObsSearchResult getObsByAuditUser(ObsSearchCriteria searchCriteria);

	/**
	 * Encounters whose audit trail names the given user, or that name the given provider, most
	 * recent action first. Voided encounters are included, for the same reason voided observations
	 * are in {@link #getObsByAuditUser(ObsSearchCriteria)}.
	 *
	 * <p>The search is described by {@link EncounterSearchCriteria}; ordering defaults to the audit
	 * action the search named. At least one user or provider is required.
	 *
	 * @param searchCriteria what to search for, how to page it and how to sort it
	 * @return the matching encounters and how many there are in total
	 * @throws org.openmrs.api.APIException if no user and no provider is given
	 */
	@Authorized(PrivilegeConstants.GET_ENCOUNTERS)
	EncounterSearchResult getEncountersByAuditUser(EncounterSearchCriteria searchCriteria);
}
