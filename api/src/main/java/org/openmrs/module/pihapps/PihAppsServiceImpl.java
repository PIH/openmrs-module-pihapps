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

import lombok.Setter;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.FlushMode;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Projections;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchResult;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.module.pihapps.orders.OrderStatus;
import org.openmrs.module.pihapps.orders.PatientWithOrders;
import org.openmrs.module.pihapps.orders.PatientWithOrdersSearchResult;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.criterion.ProjectionList;
import static org.hibernate.criterion.Order.asc;
import static org.hibernate.criterion.Order.desc;
import static org.hibernate.criterion.Restrictions.and;
import static org.hibernate.criterion.Restrictions.eq;
import static org.hibernate.criterion.Restrictions.ge;
import static org.hibernate.criterion.Restrictions.gt;
import static org.hibernate.criterion.Restrictions.in;
import static org.hibernate.criterion.Restrictions.isNotNull;
import static org.hibernate.criterion.Restrictions.isNull;
import static org.hibernate.criterion.Restrictions.le;
import static org.hibernate.criterion.Restrictions.or;

@Transactional
public class PihAppsServiceImpl extends BaseOpenmrsService implements PihAppsService {

	protected Log log = LogFactory.getLog(getClass());

	@Setter
	private LabOrderConfig labOrderConfig;

	@Setter
	private LocationTagConfig locationTagConfig;

	@Setter
	private LocationService locationService;

	@Setter
	private EncounterService encounterService;

	@Setter
	private OrderService orderService;

	@Setter
	private ObsService obsService;

	@Setter
	private DbSessionFactory sessionFactory;

	@Override
	@Transactional
	@Authorized(PrivilegeConstants.MANAGE_LOCATIONS)
	public void updateVisitAndLoginLocations(List<Location> visitLocations, List<Location> loginLocations) {
		LocationTag visitLocationTag = locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_VISITS);
		LocationTag loginLocationTag = locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN);
		for (Location l : locationService.getAllLocations(true)) {
			boolean locationChanged = false;
			boolean isVisitLocation = l.getTags() != null && l.getTags().contains(visitLocationTag);
			boolean isLoginLocation = l.getTags() != null && l.getTags().contains(loginLocationTag);
			boolean shouldBeVisitLocation = visitLocations.contains(l);
			boolean shouldBeLoginLocation = loginLocations.contains(l);
			if (isVisitLocation && !shouldBeVisitLocation) {
				l.removeTag(visitLocationTag);
				locationChanged = true;
			}
			if (isLoginLocation && !shouldBeLoginLocation) {
				l.removeTag(loginLocationTag);
				locationChanged = true;
			}
			if (!isVisitLocation && shouldBeVisitLocation) {
				l.addTag(visitLocationTag);
				locationChanged = true;
			}
			if (!isLoginLocation && shouldBeLoginLocation) {
				l.addTag(loginLocationTag);
				locationChanged = true;
			}
			if (locationChanged) {
				locationService.saveLocation(l);
			}
		}
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_ORDERS)
	@SuppressWarnings({ "unchecked" })
	public OrderSearchResult getOrders(OrderSearchCriteria searchCriteria) {
		OrderSearchResult result = new OrderSearchResult();
		// First query to get total count
		Criteria c = createHibernateOrderSearchCriteria(searchCriteria, false);
		c.setProjection(Projections.rowCount());
		Long totalCount = (Long) c.list().get(0);
		result.setTotalCount(totalCount);
		// Then query to get page of results
		c = createHibernateOrderSearchCriteria(searchCriteria, true);
		c.setProjection(null);
		c.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		Integer startIndex = searchCriteria.getStartIndex();
		Integer limit = searchCriteria.getLimit();
		if (limit != null) {
			startIndex = startIndex == null ? 0 : startIndex;
			c.setFirstResult(startIndex);
			c.setMaxResults(limit);
		}
		List<Order> orders = c.list();
		result.setOrders(orders);
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_PATIENTS)
	@SuppressWarnings({ "unchecked" })
	public PatientWithOrdersSearchResult getPatientsWithOrders(OrderSearchCriteria searchCriteria) {
		PatientWithOrdersSearchResult result = new PatientWithOrdersSearchResult();
		// First query to get total count of distinct patients
		Criteria c = createHibernateOrderSearchCriteria(searchCriteria, false);
		c.setProjection(Projections.countDistinct("patient"));
		Long totalCount = (Long) c.list().get(0);
		result.setTotalCount(totalCount);
		// Query to get page of patients, grouped and ordered using the same sort criteria as the order-level query.
		// For each sort field: DESC → MAX(field) DESC, ASC → MIN(field) ASC — surfaces the "most extreme" order per patient.
		List<SortCriteria> sortCriteriaList = searchCriteria.getSortCriteria();
		if (sortCriteriaList == null || sortCriteriaList.isEmpty()) {
			sortCriteriaList = new ArrayList<>();
			sortCriteriaList.add(new SortCriteria("urgency", SortCriteria.Direction.DESC));
			sortCriteriaList.add(new SortCriteria("dateActivated", SortCriteria.Direction.ASC));
		}
		c = createHibernateOrderSearchCriteria(searchCriteria, false);
		ProjectionList projList = Projections.projectionList();
		projList.add(Projections.groupProperty("patient"), "patient");
		for (int i = 0; i < sortCriteriaList.size(); i++) {
			SortCriteria sc = sortCriteriaList.get(i);
			String alias = "sortCol" + i;
			if (sc.getDirection() == SortCriteria.Direction.DESC) {
				projList.add(Projections.max(sc.getField()), alias);
			} else {
				projList.add(Projections.min(sc.getField()), alias);
			}
		}
		c.setProjection(projList);
		for (int i = 0; i < sortCriteriaList.size(); i++) {
			String alias = "sortCol" + i;
			if (sortCriteriaList.get(i).getDirection() == SortCriteria.Direction.DESC) {
				c.addOrder(desc(alias));
			} else {
				c.addOrder(asc(alias));
			}
		}
		Integer startIndex = searchCriteria.getStartIndex();
		Integer limit = searchCriteria.getLimit();
		if (limit != null) {
			startIndex = startIndex == null ? 0 : startIndex;
			c.setFirstResult(startIndex);
			c.setMaxResults(limit);
		}
		List<Patient> patients = new ArrayList<>();
		for (Object[] row : (List<Object[]>) c.list()) {
			patients.add((Patient) row[0]);
		}
		if (patients.isEmpty()) {
			return result;
		}
		// Retrieve orders for the page's patients and organize in the same patient order
		c = createHibernateOrderSearchCriteria(searchCriteria, true);
		c.add(in("patient", patients));
		c.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		List<Order> orders = c.list();
		Map<Patient, List<Order>> ordersForPatient = new LinkedHashMap<>();
		for (Patient p : patients) {
			ordersForPatient.put(p, new ArrayList<>());
		}
		for (Order o : orders) {
			List<Order> patientOrders = ordersForPatient.get(o.getPatient());
			if (patientOrders != null) {
				patientOrders.add(o);
			}
		}
		for (Patient p : ordersForPatient.keySet()) {
			result.getPatients().add(new PatientWithOrders(p, ordersForPatient.get(p)));
		}
		return result;
	}

	@SuppressWarnings({"deprecation"})
	private Criteria createHibernateOrderSearchCriteria(OrderSearchCriteria searchCriteria, boolean applySortCriteria) {
		Date now = new Date();
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Order.class);
		c.add(eq("voided", false));
		if (searchCriteria.getOrderTypes() != null && !searchCriteria.getOrderTypes().isEmpty()) {
			c.add(in("orderType", searchCriteria.getOrderTypes()));
		}
		c.add(in("action", Order.Action.NEW, Order.Action.REVISE, Order.Action.RENEW)); // Exclude DC orders

		if (searchCriteria.getPatient() != null) {
			c.add(eq("patient", searchCriteria.getPatient()));
		}
		if (searchCriteria.getConcept() != null) {
			c.add(eq("concept", searchCriteria.getConcept()));
		}
		if (StringUtils.isNotBlank(searchCriteria.getAccessionNumber())) {
			c.add(eq("accessionNumber", searchCriteria.getAccessionNumber()).ignoreCase());
		}
		if (searchCriteria.getOrderNumbers() != null && !searchCriteria.getOrderNumbers().isEmpty()) {
			c.add(in("orderNumber", searchCriteria.getOrderNumbers()));
		}
		if (searchCriteria.getOrderLocations() != null && !searchCriteria.getOrderLocations().isEmpty()) {
			Set<Location> locations = new HashSet<>();
			for (Location location : searchCriteria.getOrderLocations()) {
				locations.addAll(locationTagConfig.getLocationAndDescendentLocations(location));
			}
			c.createAlias("encounter", "e");
			c.add(in("e.location", locations));
		}
		if (searchCriteria.getActivatedOnOrBefore() != null) {
			Date onOrBefore = OpenmrsUtil.getLastMomentOfDay(searchCriteria.getActivatedOnOrBefore());
			c.add(le("dateActivated", onOrBefore));
		}
		if (searchCriteria.getActivatedOnOrAfter() != null) {
			Date onOrAfter = OpenmrsUtil.firstSecondOfDay(searchCriteria.getActivatedOnOrAfter());
			c.add(ge("dateActivated", onOrAfter));
		}
		if (searchCriteria.getOrderStatus() != null && !searchCriteria.getOrderStatus().isEmpty()) {
			Criterion[] orderStatusCriteria = new Criterion[searchCriteria.getOrderStatus().size()];
			for (int i = 0; i < searchCriteria.getOrderStatus().size(); i++) {
				OrderStatus orderStatus = searchCriteria.getOrderStatus().get(i);
				if (orderStatus == OrderStatus.ACTIVE) {
					Criterion orderIsNotExpired = or(isNull("autoExpireDate"), gt("autoExpireDate", now));
					Criterion orderIsNotStopped = or(isNull("dateStopped")); // This should never be in the future
					orderStatusCriteria[i] = and(orderIsNotExpired, orderIsNotStopped);
				}
				else if (orderStatus == OrderStatus.EXPIRED) {
					orderStatusCriteria[i] = le("autoExpireDate", now);
				}
				else if (orderStatus == OrderStatus.STOPPED) {
					orderStatusCriteria[i] = isNotNull("dateStopped");
				}
			}
			c.add(or(orderStatusCriteria));
		}
		List<Criterion> fulfillerStatusCriteria = new ArrayList<>();
		if (searchCriteria.getFulfillerStatuses() != null && !searchCriteria.getFulfillerStatuses().isEmpty()) {
			for (Order.FulfillerStatus fulfillerStatus : searchCriteria.getFulfillerStatuses()) {
				fulfillerStatusCriteria.add(eq("fulfillerStatus", fulfillerStatus));
			}
		}
		if (searchCriteria.getIncludeNullFulfillerStatus() == Boolean.TRUE) {
			fulfillerStatusCriteria.add(isNull("fulfillerStatus"));
		}
		else if (searchCriteria.getIncludeNullFulfillerStatus() == Boolean.FALSE) {
			fulfillerStatusCriteria.add(isNotNull("fulfillerStatus"));
		}
		if (!fulfillerStatusCriteria.isEmpty()) {
			c.add(or(fulfillerStatusCriteria.toArray(new Criterion[0])));
		}

		if (applySortCriteria) {
			List<SortCriteria> sortCriteriaList = searchCriteria.getSortCriteria();
			if (sortCriteriaList == null) {
				sortCriteriaList = new ArrayList<>();
			}
			if (sortCriteriaList.isEmpty()) {
				sortCriteriaList.add(new SortCriteria("urgency", SortCriteria.Direction.DESC)); // TODO: This is a hack that only works because STAT is alphabetically last
				sortCriteriaList.add(new SortCriteria("dateActivated", SortCriteria.Direction.ASC));
			}
			for (SortCriteria sortCriteria : sortCriteriaList) {
				if (sortCriteria.getDirection() == SortCriteria.Direction.DESC) {
					c.addOrder(desc(sortCriteria.getField()));
				} else {
					c.addOrder(asc(sortCriteria.getField()));
				}
			}
		}

		return c;
	}

	@Override
	@Transactional
	@Authorized(PrivilegeConstants.ADD_ENCOUNTERS)
	public EncounterFulfillingOrders saveEncounterFulfillingOrders(EncounterFulfillingOrders encounterFulfillingOrders) {
		// We need to ensure that the session does not flush when we retrieve this concept, to avoid errors with ImmutableObsInterceptor
		Concept accessionNumberConcept;
		FlushMode flushMode = sessionFactory.getCurrentSession().getFlushMode();
		try {
			sessionFactory.getCurrentSession().setFlushMode(FlushMode.MANUAL);
			accessionNumberConcept = labOrderConfig.getLabIdentifierConcept();
		}
		finally {
			sessionFactory.getCurrentSession().setFlushMode(flushMode);
		}
		if (accessionNumberConcept == null) {
			throw new IllegalArgumentException("Accession Number Concept configuration is required");
		}
		if (encounterFulfillingOrders.getEncounter() == null) {
			throw new  IllegalArgumentException("Encounter is required");
		}
		Encounter encounter = encounterFulfillingOrders.getEncounter();
		String accessionNumber = null;
		for (Obs obs : encounter.getObsAtTopLevel(true)) {
			if (obs.getConcept().equals(accessionNumberConcept)) {
				accessionNumber = BooleanUtils.isTrue(obs.getVoided()) ? "" : obs.getValueText();
			}
		}
		encounterService.saveEncounter(encounter);
		if (encounterFulfillingOrders.getOrders() != null) {
			for (Order order : encounterFulfillingOrders.getOrders()) {
				if (!encounter.getPatient().equals(order.getPatient())) {
					throw new IllegalArgumentException("Order " + order.getUuid() + " does not belong to the same patient as the encounter");
				}
				Order.FulfillerStatus fulfillerStatus = order.getFulfillerStatus();
				if (fulfillerStatus == null || fulfillerStatus == Order.FulfillerStatus.RECEIVED) {
					fulfillerStatus = Order.FulfillerStatus.IN_PROGRESS;
				}
				if (accessionNumber != null) {
					order.setAccessionNumber(accessionNumber);
				}
				orderService.updateOrderFulfillerStatus(order, fulfillerStatus, null, accessionNumber);
			}
		}
		return encounterFulfillingOrders;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_ENCOUNTERS)
	@SuppressWarnings({ "unchecked" })
	public EncounterFulfillingOrders getEncounterFulfillingOrders(String encounterUuid) {
		Encounter encounter = encounterService.getEncounterByUuid(encounterUuid);
		if (encounter == null) {
			return null;
		}
		EncounterFulfillingOrders encounterFulfillingOrders = new EncounterFulfillingOrders();
		encounterFulfillingOrders.setEncounter(encounter);
		List<Concept> linkingConcepts = labOrderConfig.getFulfillerEncounterLinkingConcepts();
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("voided", false));
		c.add(eq("encounter", encounter));
		c.add(isNotNull("order"));
		if (!linkingConcepts.isEmpty()) {
			c.add(in("concept", linkingConcepts));
		}
		c.setProjection(Projections.distinct(Projections.property("order")));
		encounterFulfillingOrders.setOrders(c.list());
		return encounterFulfillingOrders;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_ENCOUNTERS)
	@SuppressWarnings({ "unchecked" })
	public Encounter getFulfillerEncounterForOrder(Order order) {
		List<Concept> linkingConcepts = labOrderConfig.getFulfillerEncounterLinkingConcepts();
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("voided", false));
		c.add(eq("order", order));
		c.add(isNotNull("encounter"));
		if (!linkingConcepts.isEmpty()) {
			c.add(in("concept", linkingConcepts));
		}
		c.addOrder(desc("obsDatetime"));
		c.setMaxResults(1);
		List<Obs> l = c.list();
		if (l == null || l.isEmpty()) {
			return null;
		}
		return l.get(0).getEncounter();
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_OBS)
	@SuppressWarnings({ "unchecked" })
	public Obs getReasonOrderNotFulfilled(Order order) {
		Concept reasonConcept = labOrderConfig.getReasonTestNotPerformedQuestion();
		if (reasonConcept == null) {
			return null;
		}
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("voided", false));
		c.add(eq("person", order.getPatient()));
		c.add(eq("concept", reasonConcept));
		c.add(eq("order", order));
		c.addOrder(desc("obsDatetime"));
		c.setMaxResults(1);
		List<Obs> l = c.list();
		if (l == null || l.isEmpty()) {
			return null;
		}
		return l.get(0);
	}

	@Override
	@Transactional
	@Authorized(PrivilegeConstants.EDIT_ORDERS)
	public void markOrdersAsNotFulfilled(List<Order> orders, Concept reason) {
		for (Order order : orders) {
			orderService.updateOrderFulfillerStatus(order, Order.FulfillerStatus.EXCEPTION, null);
			Obs existingValue = getReasonOrderNotFulfilled(order);
			if (reason != null) {
				if (labOrderConfig.getReasonTestNotPerformedQuestion() == null) {
					throw new IllegalArgumentException("Reason test not performed question is not configured");
				}
				if (existingValue != null) {
					if (reason.equals(existingValue.getValueCoded())) {
						continue;
					}
					else {
						obsService.voidObs(existingValue, "Updated by pihAppsService.markOrdersAsNotFulfilled");
					}
				}
				Obs obs = new Obs();
				obs.setPerson(order.getPatient());
				obs.setObsDatetime(new Date());
				obs.setConcept(labOrderConfig.getReasonTestNotPerformedQuestion());
				obs.setOrder(order);
				obs.setValueCoded(reason);
				obs.setAccessionNumber(order.getAccessionNumber());
				obs.setComment("result-entry-form^did-not-perform-dropdown"); // This is here for backwards-compatibility with the labworkflow owa
				if (existingValue != null) {
					obs.setPreviousVersion(existingValue);
					obs.setEncounter(existingValue.getEncounter());
				}
				obsService.saveObs(obs, "");
			}
			else if (existingValue != null) {
				obsService.voidObs(existingValue, "Voided by pihAppsService.markOrdersAsNotFulfilled");
			}
		}
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_PATIENTS)
	@SuppressWarnings({ "unchecked" })
	public ObsSearchResult getObs(ObsSearchCriteria searchCriteria) {
		ObsSearchResult result = new ObsSearchResult();
		// First query to get total count
		Criteria c = createHibernateObsSearchCriteria(searchCriteria, false);
		c.setProjection(Projections.rowCount());
		Long totalCount = (Long) c.list().get(0);
		result.setTotalCount(totalCount);
		// Then query to get page of results
		c = createHibernateObsSearchCriteria(searchCriteria, true);
		c.setProjection(null);
		Integer startIndex = searchCriteria.getStartIndex();
		Integer limit = searchCriteria.getLimit();
		if (limit != null) {
			startIndex = startIndex == null ? 0 : startIndex;
			c.setFirstResult(startIndex);
			c.setMaxResults(limit);
		}
		List<Obs> obs = c.list();
		result.setObs(obs);
		return result;
	}

	@SuppressWarnings({ "deprecation" })
	private Criteria createHibernateObsSearchCriteria(ObsSearchCriteria searchCriteria, boolean applySortCriteria) {
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("voided", false));
		if (searchCriteria.getPatient() != null) {
			c.add(eq("person", searchCriteria.getPatient()));
		}
		if (searchCriteria.getConcepts() != null) {
			c.add(in("concept", searchCriteria.getConcepts()));
		}
		if (searchCriteria.getOnOrBefore() != null) {
			Date onOrBefore = OpenmrsUtil.getLastMomentOfDay(searchCriteria.getOnOrBefore());
			c.add(le("obsDatetime", onOrBefore));
		}
		if (searchCriteria.getOnOrAfter() != null) {
			Date onOrAfter = OpenmrsUtil.firstSecondOfDay(searchCriteria.getOnOrAfter());
			c.add(ge("obsDatetime", onOrAfter));
		}
		if (applySortCriteria) {
			if (searchCriteria.getSortCriteria() != null) {
				for (SortCriteria sortCriteria : searchCriteria.getSortCriteria()) {
					if (sortCriteria.getDirection() == SortCriteria.Direction.DESC) {
						c.addOrder(desc(sortCriteria.getField()));
					} else {
						c.addOrder(asc(sortCriteria.getField()));
					}
				}

			}
		}
		return c;
	}
}
