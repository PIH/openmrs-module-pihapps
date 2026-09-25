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

	/**
	 * Searches observations by whatever {@link ObsSearchCriteria} names: the patient, the concepts,
	 * the users in their audit trail, and a date range over either the observation's own datetime or
	 * the audit action. Every filter narrows, and a criteria naming none of them matches every
	 * observation, so a caller that means to search rather than to list is responsible for
	 * narrowing it.
	 *
	 * <p>Voided observations are left out unless the criteria ask for them. An audit does ask: they
	 * are the whole point of a voidedBy search, and what an auditor looking at what a user created
	 * most wants to see.
	 *
	 * <p>Ordering is the caller's to set, and paging without one is not deterministic. An audit
	 * wants the most recent audit action first, which means ordering by the column belonging to the
	 * action it named rather than by the observation's own datetime, with the obs id breaking ties
	 * so that paging cannot repeat or skip a row.
	 *
	 * @param searchCriteria what to search for, how to page it and how to sort it
	 * @return the matching observations and how many there are in total
	 */
	@Authorized(PrivilegeConstants.GET_OBS)
	ObsSearchResult getObs(ObsSearchCriteria searchCriteria);

	/**
	 * Searches encounters by whatever {@link EncounterSearchCriteria} names: the users in their
	 * audit trail, the provider recorded on them, their type, and a date range. Every filter
	 * narrows, and a criteria naming none of them matches every encounter, so a caller that means
	 * to search rather than to list is responsible for narrowing it.
	 *
	 * <p>Voided encounters are left out unless the criteria ask for them. An audit does ask: they
	 * are the whole point of a voidedBy search, and what an auditor looking at what a user entered
	 * most wants to see.
	 *
	 * <p>Ordering is the caller's to set, as it is on {@link #getObs(ObsSearchCriteria)},
	 * and paging without one is not deterministic. An audit wants the most recent audit action
	 * first, which means ordering by the column belonging to the action it named — or by the
	 * encounter's own datetime where only a provider was named — with the encounter id breaking
	 * ties so that paging cannot repeat or skip a row.
	 *
	 * @param searchCriteria what to search for, how to page it and how to sort it
	 * @return the matching encounters and how many there are in total
	 */
	@Authorized(PrivilegeConstants.GET_ENCOUNTERS)
	EncounterSearchResult getEncounters(EncounterSearchCriteria searchCriteria);
}
