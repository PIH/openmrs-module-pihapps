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
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.api.db.hibernate.ImmutableObsInterceptor;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.pihapps.encounter.EncounterSearchCriteria;
import org.openmrs.module.pihapps.encounter.EncounterSearchResult;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchResult;
import org.openmrs.module.pihapps.orders.EncounterFulfillingOrders;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEvent;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEventType;
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.module.pihapps.orders.OrderStatus;
import org.openmrs.module.pihapps.orders.PatientWithOrders;
import org.openmrs.module.pihapps.orders.PatientWithOrdersSearchResult;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import static org.hibernate.criterion.Restrictions.not;
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
		long startMillis = System.currentTimeMillis();
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
		logQueryTiming("getOrders", startMillis, searchCriteria, totalCount, orders.size());
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_PATIENTS)
	@SuppressWarnings({ "unchecked" })
	public PatientWithOrdersSearchResult getPatientsWithOrders(OrderSearchCriteria searchCriteria) {
		long startMillis = System.currentTimeMillis();
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
			logQueryTiming("getPatientsWithOrders", startMillis, searchCriteria, result.getTotalCount(), 0);
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
		logQueryTiming("getPatientsWithOrders", startMillis, searchCriteria, result.getTotalCount(), orders.size());
		return result;
	}

	/**
	 * Logs elapsed time and the resolved filter shape for a Lab Order List query, so slow requests are
	 * diagnosable from application logs alone on servers where we don't have direct database access.
	 * Deliberately avoids logging patient identifiers - only whether a patient filter was applied.
	 */
	private void logQueryTiming(String operation, long startMillis, OrderSearchCriteria searchCriteria, Long totalCount, int rowsReturned) {
		long elapsedMillis = System.currentTimeMillis() - startMillis;
		log.debug(operation + " took " + elapsedMillis + "ms"
				+ " (totalCount=" + totalCount + ", rowsReturned=" + rowsReturned
				+ ", orderTypes=" + (searchCriteria.getOrderTypes() == null ? "any" : searchCriteria.getOrderTypes().size())
				+ ", orderLocations=" + (searchCriteria.getOrderLocations() == null ? "any" : searchCriteria.getOrderLocations().size())
				+ ", hasPatientFilter=" + (searchCriteria.getPatient() != null)
				+ ", hasConceptFilter=" + (searchCriteria.getConcept() != null)
				+ ", hasAccessionNumberFilter=" + StringUtils.isNotBlank(searchCriteria.getAccessionNumber())
				+ ", activatedOnOrAfter=" + searchCriteria.getActivatedOnOrAfter()
				+ ", activatedOnOrBefore=" + searchCriteria.getActivatedOnOrBefore()
				+ ", fulfillmentStatuses=" + searchCriteria.getOrderFulfillmentStatuses()
				+ ", startIndex=" + searchCriteria.getStartIndex()
				+ ", limit=" + searchCriteria.getLimit() + ")");
	}

	/**
	 * Matches orders with a non-voided RESULTS_SUBMITTED event that no later non-voided validation event follows,
	 * ie. orders whose latest validation event is RESULTS_SUBMITTED
	 */
	private Criterion awaitingValidationCriterion() {
		DetachedCriteria submitted = DetachedCriteria.forClass(OrderFulfillmentEvent.class, "submitted");
		submitted.add(Restrictions.eqProperty("submitted.order.orderId", "this.orderId"));
		submitted.add(Restrictions.eq("submitted.voided", false));
		submitted.add(Restrictions.eq("submitted.eventType", OrderFulfillmentEventType.RESULTS_SUBMITTED));
		DetachedCriteria later = DetachedCriteria.forClass(OrderFulfillmentEvent.class, "later");
		later.add(Restrictions.eqProperty("later.order.orderId", "this.orderId"));
		later.add(Restrictions.eq("later.voided", false));
		later.add(Restrictions.in("later.eventType", OrderFulfillmentEventType.RESULT_VALIDATION));
		later.add(Restrictions.or(
				Restrictions.gtProperty("later.eventDatetime", "submitted.eventDatetime"),
				Restrictions.and(Restrictions.eqProperty("later.eventDatetime", "submitted.eventDatetime"),
						Restrictions.gtProperty("later.orderFulfillmentEventId", "submitted.orderFulfillmentEventId"))));
		submitted.add(Subqueries.notExists(later.setProjection(Projections.id())));
		return Subqueries.exists(submitted.setProjection(Projections.id()));
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
		if (searchCriteria.getOrderFulfillmentStatuses() != null && !searchCriteria.getOrderFulfillmentStatuses().isEmpty()) {
			List<Criterion> statusCriteria = new ArrayList<>();
			for (OrderFulfillmentStatus status : searchCriteria.getOrderFulfillmentStatuses()) {
				if (status == null) {
					continue;
				}
				Criterion orderStatusClause = null;
				if (status.getOrderStatus() == OrderStatus.ACTIVE) {
					Criterion orderIsNotExpired = or(isNull("autoExpireDate"), gt("autoExpireDate", now));
					Criterion orderIsNotStopped = or(isNull("dateStopped")); // This should never be in the future
					orderStatusClause = and(orderIsNotExpired, orderIsNotStopped);
				}
				else if (status.getOrderStatus() == OrderStatus.EXPIRED) {
					orderStatusClause = and(le("autoExpireDate", now), isNull("dateStopped"));
				}
				else if (status.getOrderStatus() == OrderStatus.STOPPED) {
					orderStatusClause = isNotNull("dateStopped");
				}

				List<Criterion> fulfillerStatusClauses = new ArrayList<>();
				if (status.getFulfillerStatuses() != null) {
					for (Order.FulfillerStatus fulfillerStatus : status.getFulfillerStatuses()) {
						fulfillerStatusClauses.add(eq("fulfillerStatus", fulfillerStatus));
					}
				}
				if (status.getIncludeNullFulfillerStatus() == Boolean.TRUE) {
					fulfillerStatusClauses.add(isNull("fulfillerStatus"));
				}
				else if (status.getIncludeNullFulfillerStatus() == Boolean.FALSE) {
					fulfillerStatusClauses.add(isNotNull("fulfillerStatus"));
				}
				Criterion fulfillerStatusClause = fulfillerStatusClauses.isEmpty() ? null : or(fulfillerStatusClauses.toArray(new Criterion[0]));
				if (status.getAwaitingValidation() != null && labOrderConfig.getRequireResultValidation()) {
					Criterion awaitingValidation = awaitingValidationCriterion();
					Criterion validationClause = status.getAwaitingValidation() ? awaitingValidation : not(awaitingValidation);
					fulfillerStatusClause = fulfillerStatusClause == null ? validationClause : and(fulfillerStatusClause, validationClause);
				}

				Criterion statusCriterion;
				if (orderStatusClause != null && fulfillerStatusClause != null) {
					statusCriterion = and(orderStatusClause, fulfillerStatusClause);
				}
				else if (orderStatusClause != null) {
					statusCriterion = orderStatusClause;
				}
				else {
					statusCriterion = fulfillerStatusClause;
				}
				if (statusCriterion != null) {
					statusCriteria.add(statusCriterion);
				}
			}
			if (!statusCriteria.isEmpty()) {
				c.add(or(statusCriteria.toArray(new Criterion[0])));
			}
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
		// We need to ensure that the session does not flush when we retrieve this configuration, to avoid errors with
		// ImmutableObsInterceptor and with the obs of the encounter that are not saved yet
		Concept accessionNumberConcept;
		boolean requireResultValidation;
		FlushMode flushMode = sessionFactory.getCurrentSession().getFlushMode();
		try {
			sessionFactory.getCurrentSession().setFlushMode(FlushMode.MANUAL);
			accessionNumberConcept = labOrderConfig.getLabIdentifierConcept();
			requireResultValidation = labOrderConfig.getRequireResultValidation();
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
		// With validation required, results submitted as completed await validation, and new results of an order
		// that is not yet completed are preliminary.  Read what is persisted before any order is changed.
		List<Order> submittedOrders = new ArrayList<>();
		Map<Order, Order.FulfillerStatus> persistedStatuses = new HashMap<>();
		Map<Order, Set<Concept>> resultConcepts = new HashMap<>();
		Map<Order, Set<Integer>> persistedObsIds = new HashMap<>();
		try {
			sessionFactory.getCurrentSession().setFlushMode(FlushMode.MANUAL);
			if (requireResultValidation && encounterFulfillingOrders.getOrders() != null) {
				// Locked first, in order of id as validation does, so that the state read is not changed by a validation
				// before this save completes
				for (Order order : byId(encounterFulfillingOrders.getOrders())) {
					persistedStatuses.put(order, lockFulfillerStatus(order));
				}
				for (Order order : encounterFulfillingOrders.getOrders()) {
					Order.FulfillerStatus persistedStatus = persistedStatuses.get(order);
					resultConcepts.put(order, getLabResultConcepts(order));
					if (persistedStatus == Order.FulfillerStatus.COMPLETED) {
						persistedObsIds.put(order, lockSavedResultObs(order, resultConcepts.get(order)).keySet());
					}
				}
			}
			applyObsGroupResults(encounter, encounterFulfillingOrders);
		}
		finally {
			sessionFactory.getCurrentSession().setFlushMode(flushMode);
		}
		// Update orders before saving the encounter: the obs INSERT (from saveEncounter) needs a shared lock on
		// the referenced order rows for FK constraint verification. Updating orders first acquires exclusive locks
		// on those rows, preventing contention with concurrent transactions that also hold exclusive order locks.
		if (encounterFulfillingOrders.getOrders() != null) {
			for (Order order : encounterFulfillingOrders.getOrders()) {
				if (!encounter.getPatient().equals(order.getPatient())) {
					throw new IllegalArgumentException("Order " + order.getUuid() + " does not belong to the same patient as the encounter");
				}
				if (accessionNumber != null) {
					order.setAccessionNumber(accessionNumber);
				}
				Order.FulfillerStatus fulfillerStatus = order.getFulfillerStatus();
				if (fulfillerStatus == null) {
					fulfillerStatus = Order.FulfillerStatus.IN_PROGRESS;
				}
				if (requireResultValidation) {
					// A completed order stays completed unless marked not performed.  Otherwise only a validator completes it: results submitted as completed await validation
					boolean completed = persistedStatuses.get(order) == Order.FulfillerStatus.COMPLETED;
					if (completed) {
						if (fulfillerStatus != Order.FulfillerStatus.EXCEPTION && fulfillerStatus != Order.FulfillerStatus.DECLINED) {
							fulfillerStatus = Order.FulfillerStatus.COMPLETED;
						}
					}
					else {
						if (fulfillerStatus == Order.FulfillerStatus.COMPLETED) {
							fulfillerStatus = Order.FulfillerStatus.IN_PROGRESS;
							submittedOrders.add(order);
						}
						markNewResultsPreliminary(encounter, order, resultConcepts.get(order));
					}
				}
				orderService.updateOrderFulfillerStatus(order, fulfillerStatus, null, accessionNumber);
			}
		}
		encounterService.saveEncounter(encounter);
		if (requireResultValidation && encounterFulfillingOrders.getOrders() != null) {
			// Submitted results of an order that is no longer in progress, eg marked not performed, are withdrawn, so
			// that they do not await validation again if the order is later in progress again
			List<Order> leftInProgress = new ArrayList<>();
			for (Order order : encounterFulfillingOrders.getOrders()) {
				if (persistedStatuses.get(order) != Order.FulfillerStatus.COMPLETED
						&& order.getFulfillerStatus() != Order.FulfillerStatus.IN_PROGRESS) {
					leftInProgress.add(order);
				}
			}
			withdrawSubmittedResults(leftInProgress);
			// Changes to the results of a completed order are recorded, so that they can be reviewed
			if (!persistedObsIds.isEmpty()) {
				sessionFactory.getCurrentSession().flush();
				for (Order order : persistedObsIds.keySet()) {
					if (order.getFulfillerStatus() == Order.FulfillerStatus.COMPLETED) {
						Set<Integer> saved = lockSavedResultObs(order, resultConcepts.get(order)).keySet();
						if (!persistedObsIds.get(order).equals(saved)) {
							Set<Integer> added = new HashSet<>(saved);
							added.removeAll(persistedObsIds.get(order));
							releaseNewResults(added);
							saveValidationEvent(order, OrderFulfillmentEventType.RESULTS_AMENDED, null);
						}
					}
				}
			}
		}
		if (!submittedOrders.isEmpty()) {
			Map<Order, OrderFulfillmentEventType> latestEvents = lockLatestValidationEventTypes(submittedOrders);
			for (Order order : submittedOrders) {
				if (latestEvents.get(order) != OrderFulfillmentEventType.RESULTS_SUBMITTED) {
					OrderFulfillmentEvent event = new OrderFulfillmentEvent();
					event.setOrder(order);
					event.setEventType(OrderFulfillmentEventType.RESULTS_SUBMITTED);
					persistEvent(event);
				}
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

	/**
	 * Batched equivalent of {@link #getFulfillerEncounterForOrder(Order)}: fetches the fulfiller
	 * encounter for every given order in a single query, instead of one query per order (which was
	 * previously invoked once per row when rendering the Lab Order List, an N+1 that dominated as the
	 * list of returned/nested orders grew).
	 */
	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_ENCOUNTERS)
	@SuppressWarnings({ "unchecked" })
	public Map<Order, Encounter> getFulfillerEncountersForOrders(List<Order> orders) {
		long startMillis = System.currentTimeMillis();
		Map<Order, Encounter> result = new HashMap<>();
		if (orders == null || orders.isEmpty()) {
			return result;
		}
		List<Concept> linkingConcepts = labOrderConfig.getFulfillerEncounterLinkingConcepts();
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("voided", false));
		c.add(in("order", orders));
		c.add(isNotNull("encounter"));
		if (!linkingConcepts.isEmpty()) {
			c.add(in("concept", linkingConcepts));
		}
		c.addOrder(desc("obsDatetime"));
		// Sorted globally by obsDatetime desc, so the first Obs seen per order is its most recent - matching
		// the setMaxResults(1)-per-order semantics of the single-order method above.
		for (Obs obs : (List<Obs>) c.list()) {
			result.putIfAbsent(obs.getOrder(), obs.getEncounter());
		}
		long elapsedMillis = System.currentTimeMillis() - startMillis;
		log.debug("getFulfillerEncountersForOrders took " + elapsedMillis + "ms for " + orders.size() + " orders");
		return result;
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
		if (labOrderConfig.getRequireResultValidation()) {
			withdrawSubmittedResults(orders);
		}
	}

	@Override
	@Transactional
	@Authorized(PrivilegeConstants.EDIT_ORDERS)
	public void revertOrdersToOrdered(List<Order> orders) {
		for (Order order : orders) {
			Obs existingReason = getReasonOrderNotFulfilled(order);
			if (existingReason != null) {
				obsService.voidObs(existingReason, "Reverted to ordered by pihAppsService.revertOrdersToOrdered");
			}
			orderService.updateOrderFulfillerStatus(order, Order.FulfillerStatus.RECEIVED, null);
		}
		if (labOrderConfig.getRequireResultValidation()) {
			withdrawSubmittedResults(orders);
		}
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_CONCEPTS)
	public Set<Concept> getLabResultConcepts(Order order) {
		Set<Concept> ret = new HashSet<>();
		addConceptAndSetMembers(order.getConcept(), ret);
		return ret;
	}

	private void addConceptAndSetMembers(Concept concept, Set<Concept> concepts) {
		if (concept != null && concepts.add(concept) && concept.getSetMembers() != null) {
			for (Concept setMember : concept.getSetMembers()) {
				addConceptAndSetMembers(setMember, concepts);
			}
		}
	}

	/*
	 * Locking reads.  Once an order is locked, what decides what happens to it is read with SELECT ... FOR UPDATE, in
	 * SQL: a locking read returns what is committed in the database, whatever the transaction isolation level (MySQL's
	 * REPEATABLE_READ would otherwise give a snapshot from before the lock), and SQL reads the database rather than the
	 * copies already in the Hibernate session.  Callers are responsible for ensuring these do not flush the session.
	 */

	/**
	 * Locks the order and returns its fulfiller status as committed in the database
	 */
	private Order.FulfillerStatus lockFulfillerStatus(Order order) {
		if (order.getOrderId() == null) {
			return null;
		}
		Object status = sessionFactory.getCurrentSession()
				.createSQLQuery("select fulfiller_status from orders where order_id = :orderId for update")
				.setInteger("orderId", order.getOrderId())
				.uniqueResult();
		return status == null ? null : Order.FulfillerStatus.valueOf(status.toString());
	}

	/**
	 * @return for each given order that has any, the type of its latest non-voided validation event, as committed
	 */
	@SuppressWarnings("unchecked")
	private Map<Order, OrderFulfillmentEventType> lockLatestValidationEventTypes(List<Order> orders) {
		Map<Integer, Order> byOrderId = new HashMap<>();
		for (Order order : orders) {
			if (order.getOrderId() != null) {
				byOrderId.put(order.getOrderId(), order);
			}
		}
		Map<Order, OrderFulfillmentEventType> ret = new HashMap<>();
		if (byOrderId.isEmpty()) {
			return ret;
		}
		List<String> types = new ArrayList<>();
		for (OrderFulfillmentEventType type : OrderFulfillmentEventType.RESULT_VALIDATION) {
			types.add(type.name());
		}
		// Ordered so that the last row of each order is its latest event: by event datetime, then id
		List<Object[]> rows = sessionFactory.getCurrentSession()
				.createSQLQuery("select order_id, event_type from pihapps_order_fulfillment_event "
						+ "where voided = 0 and order_id in (:orderIds) and event_type in (:types) "
						+ "order by order_id, event_datetime, order_fulfillment_event_id for update")
				.setParameterList("orderIds", byOrderId.keySet())
				.setParameterList("types", types)
				.list();
		for (Object[] row : rows) {
			ret.put(byOrderId.get(((Number) row[0]).intValue()), OrderFulfillmentEventType.valueOf(row[1].toString()));
		}
		return ret;
	}

	/**
	 * @return the non-voided results of the given order as committed, by obs id with their uuid: obs linked to the
	 * order of one of the given result concepts, and the members of their groups, at any depth
	 */
	@SuppressWarnings("unchecked")
	private Map<Integer, String> lockSavedResultObs(Order order, Set<Concept> resultConcepts) {
		Map<Integer, String> ret = new LinkedHashMap<>();
		if (order.getOrderId() == null || resultConcepts == null || resultConcepts.isEmpty()) {
			return ret;
		}
		List<Integer> conceptIds = new ArrayList<>();
		for (Concept concept : resultConcepts) {
			conceptIds.add(concept.getConceptId());
		}
		List<Object[]> level = sessionFactory.getCurrentSession()
				.createSQLQuery("select obs_id, uuid from obs where order_id = :orderId and voided = 0 and concept_id in (:conceptIds) for update")
				.setInteger("orderId", order.getOrderId())
				.setParameterList("conceptIds", conceptIds)
				.list();
		while (!level.isEmpty()) {
			List<Integer> ids = new ArrayList<>();
			for (Object[] row : level) {
				Integer id = ((Number) row[0]).intValue();
				if (!ret.containsKey(id)) {
					ret.put(id, row[1].toString());
					ids.add(id);
				}
			}
			if (ids.isEmpty()) {
				break;
			}
			level = sessionFactory.getCurrentSession()
					.createSQLQuery("select obs_id, uuid from obs where obs_group_id in (:ids) and voided = 0 for update")
					.setParameterList("ids", ids)
					.list();
		}
		return ret;
	}

	/**
	 * Applies the results recorded as obs groups: for each, voids the order's existing non-voided obs of its concept in
	 * the encounter, with their members, and adds the new group, if any, with its members linked to the order.  A new
	 * group that replaces a final or amended one is amended, with the replaced group as its previous version; with
	 * validation required, {@link #markNewResultsPreliminary} later makes the new results of an order that is not
	 * completed preliminary.
	 */
	private void applyObsGroupResults(Encounter encounter, EncounterFulfillingOrders encounterFulfillingOrders) {
		if (encounterFulfillingOrders.getObsGroupResults() == null) {
			return;
		}
		List<Order> orders = encounterFulfillingOrders.getOrders() == null ? Collections.<Order>emptyList() : encounterFulfillingOrders.getOrders();
		for (EncounterFulfillingOrders.ObsGroupResult result : encounterFulfillingOrders.getObsGroupResults()) {
			Order order = result.getOrder();
			Concept concept = result.getConcept();
			if (order == null || concept == null || !orders.contains(order)) {
				throw new IllegalArgumentException("An obs group result needs an order that the encounter fulfills, and a concept");
			}
			if (!getLabResultConcepts(order).contains(concept)) {
				throw new IllegalArgumentException(concept.getUuid() + " is not a result concept of order " + order.getUuid());
			}
			// The existing groups are read from the database: the encounter given may not hold all its obs, eg when
			// converted from a request that only sends the obs it changes
			Obs replaced = null;
			for (Obs existing : getSavedObsGroups(encounter, order, concept)) {
				replaced = replaced == null ? existing : replaced;
				voidWithMembers(existing, "Replaced by pihAppsService.saveEncounterFulfillingOrders");
			}
			Obs group = result.getObs();
			if (group != null) {
				if (!concept.equals(group.getConcept())) {
					throw new IllegalArgumentException("The obs group of a result must be of the result's concept");
				}
				requireNewObs(group);
				Obs.Status status = Obs.Status.FINAL;
				if (replaced != null) {
					group.setPreviousVersion(replaced);
					if (replaced.getStatus() == Obs.Status.FINAL || replaced.getStatus() == Obs.Status.AMENDED) {
						status = Obs.Status.AMENDED;
					}
				}
				prepareNewObs(group, encounter, order, status);
				encounter.addObs(group);
			}
		}
	}

	/**
	 * @return the non-voided obs of the concept linked to the order in the encounter, as committed (read with a locking
	 * read, then refreshed with their members), most recent first
	 */
	@SuppressWarnings("unchecked")
	private List<Obs> getSavedObsGroups(Encounter encounter, Order order, Concept concept) {
		if (encounter.getEncounterId() == null || order.getOrderId() == null) {
			return Collections.emptyList();
		}
		List<Number> ids = sessionFactory.getCurrentSession()
				.createSQLQuery("select obs_id from obs where encounter_id = :encounterId and order_id = :orderId "
						+ "and concept_id = :conceptId and voided = 0 order by obs_id desc for update")
				.setInteger("encounterId", encounter.getEncounterId())
				.setInteger("orderId", order.getOrderId())
				.setInteger("conceptId", concept.getConceptId())
				.list();
		List<Obs> ret = new ArrayList<>();
		for (Number id : ids) {
			Obs obs = (Obs) sessionFactory.getCurrentSession().get(Obs.class, id.intValue());
			refreshWithMembers(obs);
			ret.add(obs);
		}
		return ret;
	}

	private void refreshWithMembers(Obs obs) {
		sessionFactory.getHibernateSessionFactory().getCurrentSession().refresh(obs);
		if (obs.getGroupMembers(true) != null) {
			for (Obs member : obs.getGroupMembers(true)) {
				refreshWithMembers(member);
			}
		}
	}

	/**
	 * Makes the given new results of a completed order released: amended when they replace a result, else final.  An
	 * edited result takes the status of the copy the request loaded, which can be from before the order was locked:
	 * preliminary although a validation completed the order in the meantime
	 */
	private void releaseNewResults(Set<Integer> obsIds) {
		ImmutableObsInterceptor interceptor = Context.getRegisteredComponents(ImmutableObsInterceptor.class).get(0);
		try {
			interceptor.addMutablePropertiesForThread("status");
			for (Integer obsId : obsIds) {
				Obs obs = (Obs) sessionFactory.getCurrentSession().get(Obs.class, obsId);
				if (obs != null && obs.getStatus() == Obs.Status.PRELIMINARY) {
					obs.setStatus(obs.getPreviousVersion() != null ? Obs.Status.AMENDED : Obs.Status.FINAL);
				}
			}
			sessionFactory.getCurrentSession().flush();
		}
		finally {
			interceptor.removeMutablePropertiesForThread();
		}
	}

	/**
	 * Requires the obs and its members to be new: an existing obs given back as a result would be voided as replaced
	 */
	private void requireNewObs(Obs obs) {
		if (obs.getObsId() != null) {
			throw new IllegalArgumentException("An obs group result must be new obs, without uuids of existing obs: " + obs.getUuid());
		}
		if (obs.getGroupMembers(true) != null) {
			for (Obs member : obs.getGroupMembers(true)) {
				requireNewObs(member);
			}
		}
	}

	/**
	 * @return the orders sorted by id, new ones last: orders are locked in this order, so that two transactions
	 * locking the same orders do not deadlock
	 */
	private List<Order> byId(List<Order> orders) {
		List<Order> ret = new ArrayList<>(orders);
		ret.sort((a, b) -> a.getOrderId() == null ? (b.getOrderId() == null ? 0 : 1)
				: b.getOrderId() == null ? -1 : a.getOrderId().compareTo(b.getOrderId()));
		return ret;
	}

	private void voidWithMembers(Obs obs, String reason) {
		if (obs.getGroupMembers(false) != null) {
			for (Obs member : obs.getGroupMembers(false)) {
				voidWithMembers(member, reason);
			}
		}
		obs.setVoided(true);
		obs.setVoidedBy(Context.getAuthenticatedUser());
		obs.setDateVoided(new Date());
		obs.setVoidReason(reason);
	}

	private void prepareNewObs(Obs obs, Encounter encounter, Order order, Obs.Status status) {
		obs.setPerson(encounter.getPatient());
		obs.setEncounter(encounter);
		obs.setObsDatetime(encounter.getEncounterDatetime());
		obs.setLocation(encounter.getLocation());
		obs.setOrder(order);
		obs.setAccessionNumber(order.getAccessionNumber());
		obs.setStatus(status);
		if (obs.getGroupMembers() != null) {
			for (Obs member : obs.getGroupMembers()) {
				member.setObsGroup(obs);
				prepareNewObs(member, encounter, order, status);
			}
		}
	}

	/**
	 * Records that the results submitted for validation of each given order whose latest validation event is a
	 * submission are withdrawn
	 */
	private void withdrawSubmittedResults(List<Order> orders) {
		if (orders.isEmpty()) {
			return;
		}
		Map<Order, OrderFulfillmentEventType> latestEvents = lockLatestValidationEventTypes(orders);
		for (Order order : orders) {
			if (latestEvents.get(order) == OrderFulfillmentEventType.RESULTS_SUBMITTED) {
				saveValidationEvent(order, OrderFulfillmentEventType.RESULTS_WITHDRAWN, null);
			}
		}
	}

	/**
	 * Marks new, non-voided results of the given order in the encounter as preliminary: obs linked to the order whose
	 * concept is a result concept of the order, and the members of their groups
	 */
	private void markNewResultsPreliminary(Encounter encounter, Order order, Set<Concept> resultConcepts) {
		Set<Obs> allObs = new HashSet<>();
		List<Obs> toVisit = new ArrayList<>(encounter.getAllObs(true));
		while (!toVisit.isEmpty()) {
			Obs obs = toVisit.remove(toVisit.size() - 1);
			if (allObs.add(obs) && obs.getGroupMembers(true) != null) {
				toVisit.addAll(obs.getGroupMembers(true));
			}
		}
		for (Obs obs : allObs) {
			if (obs.getObsId() == null && !BooleanUtils.isTrue(obs.getVoided()) && isLabResult(obs, order, resultConcepts)) {
				obs.setStatus(Obs.Status.PRELIMINARY);
			}
		}
	}

	/**
	 * @return whether the obs is a result of the order: it, or an obs group it is a member of, is linked to the order
	 * and has one of the order's result concepts
	 */
	private boolean isLabResult(Obs obs, Order order, Set<Concept> resultConcepts) {
		for (Obs o = obs; o != null; o = o.getObsGroup()) {
			if (order.equals(o.getOrder()) && resultConcepts.contains(o.getConcept())) {
				return true;
			}
		}
		return false;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_ORDERS)
	public Obs.Status getNewLabResultStatus(Order order) {
		if (!labOrderConfig.getRequireResultValidation()) {
			return Obs.Status.FINAL;
		}
		Order.FulfillerStatus status = order.getFulfillerStatus();
		boolean released = status == Order.FulfillerStatus.COMPLETED || status == Order.FulfillerStatus.EXCEPTION
				|| status == Order.FulfillerStatus.DECLINED;
		return released ? Obs.Status.FINAL : Obs.Status.PRELIMINARY;
	}

	@Override
	@Transactional
	@Authorized(PrivilegeConstants.EDIT_ORDERS)
	public OrderFulfillmentEvent saveOrderFulfillmentEvent(OrderFulfillmentEvent event) {
		// Events decide whether an order's results await validation: recording or voiding one directly, rather than
		// through saving, validating or returning results, is for validators only
		if (!Context.hasPrivilege(LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS)) {
			throw new APIAuthenticationException("Privilege required: " + LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS);
		}
		return persistEvent(event);
	}

	private OrderFulfillmentEvent persistEvent(OrderFulfillmentEvent event) {
		if (event.getEventDatetime() == null) {
			event.setEventDatetime(new Date());
		}
		sessionFactory.getHibernateSessionFactory().getCurrentSession().saveOrUpdate(event);
		return event;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_ORDERS)
	@SuppressWarnings({ "unchecked" })
	public Map<Order, OrderFulfillmentEvent> getLatestOrderFulfillmentEvents(List<Order> orders, Collection<OrderFulfillmentEventType> types) {
		Map<Order, OrderFulfillmentEvent> result = new HashMap<>();
		if (orders == null || orders.isEmpty() || types == null || types.isEmpty()) {
			return result;
		}
		List<OrderFulfillmentEvent> events = sessionFactory.getHibernateSessionFactory().getCurrentSession()
				.createQuery("from OrderFulfillmentEvent e where e.voided = false and e.order in (:orders) and e.eventType in (:types) "
						+ "and not exists (from OrderFulfillmentEvent l where l.voided = false and l.order = e.order and l.eventType in (:types) "
						+ "and (l.eventDatetime > e.eventDatetime or (l.eventDatetime = e.eventDatetime "
						+ "and l.orderFulfillmentEventId > e.orderFulfillmentEventId)))")
				.setParameterList("orders", orders)
				.setParameterList("types", types)
				.list();
		for (OrderFulfillmentEvent event : events) {
			result.put(event.getOrder(), event);
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_ORDERS)
	@SuppressWarnings({ "unchecked" })
	public List<OrderFulfillmentEvent> getOrderFulfillmentEvents(Order order) {
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(OrderFulfillmentEvent.class);
		c.add(eq("voided", false));
		c.add(eq("order", order));
		c.addOrder(asc("eventDatetime"));
		c.addOrder(asc("orderFulfillmentEventId"));
		return c.list();
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_OBS)
	public Set<User> getLabResultEditors(Order order) {
		return getLabResultEditors(order, getLabResultObsIncludingVoided(order));
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_OBS)
	public List<Obs> getLabResultObs(Order order) {
		List<Obs> ret = new ArrayList<>();
		for (Obs obs : getLabResultObsIncludingVoided(order)) {
			if (!BooleanUtils.isTrue(obs.getVoided())) {
				ret.add(obs);
			}
		}
		return ret;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(value = { PrivilegeConstants.GET_ORDERS, PrivilegeConstants.GET_OBS }, requireAll = true)
	public boolean canValidateLabResults(Order order, User user) {
		if (!labOrderConfig.getRequireResultValidation()) {
			return false;
		}
		OrderFulfillmentEvent latest = getLatestOrderFulfillmentEvents(Collections.singletonList(order),
				OrderFulfillmentEventType.RESULT_VALIDATION).get(order);
		return isAwaitingValidation(order, latest == null ? null : latest.getEventType()) && !getLabResultEditors(order).contains(user);
	}

	@Override
	@Transactional
	@Authorized(LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS)
	public void validateLabResults(List<Order> orders, Map<Order, ? extends Collection<String>> reviewedResults, String comment) {
		if (reviewedResults == null) {
			throw new IllegalArgumentException("The results reviewed are required to validate them");
		}
		checkCanValidateLabResults(orders);
		Map<Order, Map<Integer, String>> resultObs = new HashMap<>();
		for (Order order : orders) {
			// The results validated must be those the validator reviewed, as committed: they may have changed since
			Map<Integer, String> current = lockSavedResultObs(order, getLabResultConcepts(order));
			Collection<String> reviewed = reviewedResults.get(order);
			if (reviewed == null || !new HashSet<>(current.values()).equals(new HashSet<>(reviewed))) {
				throw new APIException("pihapps.labResultValidation.error.resultsChanged", new Object[] { order.getOrderNumber() });
			}
			resultObs.put(order, current);
		}
		ImmutableObsInterceptor interceptor = Context.getRegisteredComponents(ImmutableObsInterceptor.class).get(0);
		try {
			// Results become final in place, rather than by void and replace, which would double the result rows
			interceptor.addMutablePropertiesForThread("status");
			for (Order order : orders) {
				for (Integer obsId : resultObs.get(order).keySet()) {
					Obs obs = (Obs) sessionFactory.getCurrentSession().get(Obs.class, obsId);
					sessionFactory.getHibernateSessionFactory().getCurrentSession().refresh(obs);
					if (obs.getStatus() == Obs.Status.PRELIMINARY) {
						obs.setStatus(Obs.Status.FINAL);
					}
				}
				orderService.updateOrderFulfillerStatus(order, Order.FulfillerStatus.COMPLETED, null);
				saveValidationEvent(order, OrderFulfillmentEventType.RESULTS_VALIDATED, StringUtils.trimToNull(comment));
			}
			sessionFactory.getCurrentSession().flush();
		}
		finally {
			interceptor.removeMutablePropertiesForThread();
		}
	}

	@Override
	@Transactional
	@Authorized(LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS)
	public void returnLabResultsForCorrection(List<Order> orders, String reason) {
		if (StringUtils.isBlank(reason)) {
			throw new APIException("pihapps.labResultValidation.error.reasonRequired", (Object[]) null);
		}
		checkCanValidateLabResults(orders);
		for (Order order : orders) {
			saveValidationEvent(order, OrderFulfillmentEventType.RESULTS_RETURNED, reason.trim());
		}
	}

	/**
	 * Locks the orders, then checks that the current user can validate the results of every one, before anything is
	 * changed
	 * @throws APIException naming the first order that cannot be validated
	 */
	private void checkCanValidateLabResults(List<Order> orders) {
		if (!labOrderConfig.getRequireResultValidation()) {
			throw new APIException("pihapps.labResultValidation.error.notEnabled", (Object[]) null);
		}
		if (orders == null || orders.isEmpty() || new HashSet<>(orders).size() != orders.size()) {
			throw new IllegalArgumentException("The orders to validate or return must be given, each once");
		}
		// Lock the orders, and read them again, so that their results cannot be saved, validated or returned by
		// another user between these checks and the changes that follow them
		for (Order order : byId(orders)) {
			sessionFactory.getHibernateSessionFactory().getCurrentSession().refresh(order, new LockOptions(LockMode.PESSIMISTIC_WRITE));
		}
		User user = Context.getAuthenticatedUser();
		Map<Order, OrderFulfillmentEventType> latestEvents = lockLatestValidationEventTypes(orders);
		for (Order order : orders) {
			if (!isAwaitingValidation(order, latestEvents.get(order))) {
				throw new APIException("pihapps.labResultValidation.error.notAwaitingValidation", new Object[] { order.getOrderNumber() });
			}
			if (getLabResultEditors(order, getLabResultObsIncludingVoided(order)).contains(user)) {
				throw new APIException("pihapps.labResultValidation.error.enteredByCurrentUser", new Object[] { order.getOrderNumber() });
			}
		}
	}

	/**
	 * @return whether the order is in progress and its latest validation event is a submission.  An order marked not
	 * performed after its results were submitted keeps that event, so its fulfiller status is checked too
	 */
	private boolean isAwaitingValidation(Order order, OrderFulfillmentEventType latestValidationEventType) {
		return order.getFulfillerStatus() == Order.FulfillerStatus.IN_PROGRESS
				&& latestValidationEventType == OrderFulfillmentEventType.RESULTS_SUBMITTED;
	}

	/**
	 * @return the results of the given order, voided ones included: obs linked to the order whose concept is a result
	 * concept of the order, and the members of their groups, whether or not the members are linked to the order
	 */
	@SuppressWarnings({ "unchecked" })
	private List<Obs> getLabResultObsIncludingVoided(Order order) {
		Set<Concept> resultConcepts = getLabResultConcepts(order);
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("order", order));
		Set<Obs> ret = new LinkedHashSet<>();
		List<Obs> toVisit = new ArrayList<>();
		for (Obs obs : (List<Obs>) c.list()) {
			if (isLabResult(obs, order, resultConcepts)) {
				toVisit.add(obs);
			}
		}
		while (!toVisit.isEmpty()) {
			Obs obs = toVisit.remove(toVisit.size() - 1);
			if (ret.add(obs) && obs.getGroupMembers(true) != null) {
				toVisit.addAll(obs.getGroupMembers(true));
			}
		}
		return new ArrayList<>(ret);
	}

	/**
	 * @return the creators and voiders of the given result obs of the order, and the users who submitted its results
	 * since they were last validated, eg results that a lab machine recorded
	 */
	private Set<User> getLabResultEditors(Order order, List<Obs> resultObs) {
		Set<User> ret = new LinkedHashSet<>();
		for (Obs obs : resultObs) {
			ret.add(obs.getCreator());
			if (obs.getVoidedBy() != null) {
				ret.add(obs.getVoidedBy());
			}
		}
		Set<User> submitters = new LinkedHashSet<>();
		for (OrderFulfillmentEvent event : getOrderFulfillmentEvents(order)) {
			if (event.getEventType() == OrderFulfillmentEventType.RESULTS_VALIDATED) {
				submitters.clear();
			}
			else if (event.getEventType() == OrderFulfillmentEventType.RESULTS_SUBMITTED && event.getCreator() != null) {
				submitters.add(event.getCreator());
			}
		}
		ret.addAll(submitters);
		return ret;
	}

	private void saveValidationEvent(Order order, OrderFulfillmentEventType eventType, String comment) {
		OrderFulfillmentEvent event = new OrderFulfillmentEvent();
		event.setOrder(order);
		event.setEventType(eventType);
		event.setComment(comment);
		persistEvent(event);
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_OBS)
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
		if (!searchCriteria.isIncludeVoided()) {
			c.add(eq("voided", false));
		}
		if (searchCriteria.getCreatedBy() != null) {
			c.add(eq("creator", searchCriteria.getCreatedBy()));
		}
		if (searchCriteria.getVoidedBy() != null) {
			c.add(eq("voidedBy", searchCriteria.getVoidedBy()));
		}
		// Each range names the column it bounds, so none of this depends on which filters are set.
		addDateBounds(c, "dateCreated", searchCriteria.getCreatedOnOrAfter(), searchCriteria.getCreatedOnOrBefore());
		addDateBounds(c, "dateVoided", searchCriteria.getVoidedOnOrAfter(), searchCriteria.getVoidedOnOrBefore());
		if (searchCriteria.getPatient() != null) {
			c.add(eq("person", searchCriteria.getPatient()));
		}
		if (searchCriteria.getConcepts() != null) {
			c.add(in("concept", searchCriteria.getConcepts()));
		}
		if (searchCriteria.getStatuses() != null) {
			c.add(in("status", searchCriteria.getStatuses()));
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

	@Override
	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_ENCOUNTERS)
	@SuppressWarnings({ "unchecked" })
	public EncounterSearchResult getEncounters(EncounterSearchCriteria searchCriteria) {
		EncounterSearchResult result = new EncounterSearchResult();
		// First query to get total count
		Criteria c = createHibernateEncounterSearchCriteria(searchCriteria, false);
		c.setProjection(Projections.rowCount());
		Long totalCount = (Long) c.list().get(0);
		result.setTotalCount(totalCount);
		// Then query to get page of results
		c = createHibernateEncounterSearchCriteria(searchCriteria, true);
		c.setProjection(null);
		Integer startIndex = searchCriteria.getStartIndex();
		Integer limit = searchCriteria.getLimit();
		if (limit != null) {
			startIndex = startIndex == null ? 0 : startIndex;
			c.setFirstResult(startIndex);
			c.setMaxResults(limit);
		}
		result.setEncounters(c.list());
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	@Authorized({ PrivilegeConstants.GET_ENCOUNTER_TYPES, PrivilegeConstants.GET_ENCOUNTERS })
	@SuppressWarnings({ "unchecked" })
	public Map<EncounterType, Long> getEncounterTypeCounts(boolean includeVoided, boolean onlyUsed) {
		// One grouped query over the encounter table rather than one count per type: the encounter
		// type column is indexed, and an implementation can have a great many types.
		List<Object[]> rows = sessionFactory.getCurrentSession()
				.createQuery("select e.encounterType.encounterTypeId, count(e) from Encounter e "
						+ (includeVoided ? "" : "where e.voided = false ")
						+ "group by e.encounterType.encounterTypeId")
				.list();
		Map<Integer, Long> countsById = new HashMap<>();
		for (Object[] row : rows) {
			countsById.put((Integer) row[0], (Long) row[1]);
		}
		Map<EncounterType, Long> counts = new LinkedHashMap<>();
		List<EncounterType> encounterTypes = new ArrayList<>(encounterService.getAllEncounterTypes(true));
		encounterTypes.sort((a, b) -> OpenmrsUtil.compareWithNullAsGreatest(a.getName(), b.getName()));
		for (EncounterType encounterType : encounterTypes) {
			Long count = countsById.get(encounterType.getEncounterTypeId());
			if (count == null && onlyUsed) {
				continue;
			}
			counts.put(encounterType, count == null ? 0L : count);
		}
		return counts;
	}

	/**
	 * Both ends run inclusively. The upper end is widened to the end of its day when it carries no
	 * time, so that a range named in days covers the whole of the last one.
	 */
	private void addDateBounds(Criteria c, String property, Date onOrAfter, Date onOrBefore) {
		if (onOrAfter != null) {
			// No adjustment: midnight is already the first moment of its day.
			c.add(ge(property, onOrAfter));
		}
		Date upperBound = PihAppsUtils.getEndOfDayIfTimeExcluded(onOrBefore);
		if (upperBound != null) {
			c.add(le(property, upperBound));
		}
	}

	@SuppressWarnings({ "deprecation" })
	private Criteria createHibernateEncounterSearchCriteria(EncounterSearchCriteria searchCriteria,
			boolean applySortCriteria) {
		Criteria c = sessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Encounter.class);
		if (!searchCriteria.isIncludeVoided()) {
			c.add(eq("voided", false));
		}
		if (searchCriteria.getEncounterType() != null) {
			c.add(eq("encounterType", searchCriteria.getEncounterType()));
		}
		if (searchCriteria.getCreatedBy() != null) {
			c.add(eq("creator", searchCriteria.getCreatedBy()));
		}
		if (searchCriteria.getChangedBy() != null) {
			c.add(eq("changedBy", searchCriteria.getChangedBy()));
		}
		if (searchCriteria.getVoidedBy() != null) {
			c.add(eq("voidedBy", searchCriteria.getVoidedBy()));
		}
		// Each range names the column it bounds, so none of this depends on which filters are set.
		addDateBounds(c, "dateCreated", searchCriteria.getCreatedOnOrAfter(), searchCriteria.getCreatedOnOrBefore());
		addDateBounds(c, "dateChanged", searchCriteria.getChangedOnOrAfter(), searchCriteria.getChangedOnOrBefore());
		addDateBounds(c, "dateVoided", searchCriteria.getVoidedOnOrAfter(), searchCriteria.getVoidedOnOrBefore());
		addDateBounds(c, "encounterDatetime", searchCriteria.getEncounterDatetimeOnOrAfter(),
			searchCriteria.getEncounterDatetimeOnOrBefore());
		if (searchCriteria.getProvider() != null) {
			// A subquery rather than a join, so that an encounter naming the provider more than
			// once is still returned once — a join would need a distinct, and an in-memory distinct
			// would be applied after paging had already counted the duplicate rows.
			DetachedCriteria encountersNamingProvider = DetachedCriteria.forClass(EncounterProvider.class, "ep")
					.createAlias("ep.encounter", "providerEncounter")
					.setProjection(Projections.property("providerEncounter.encounterId"))
					.add(eq("ep.provider", searchCriteria.getProvider()))
					.add(eq("ep.voided", false));
			c.add(Subqueries.propertyIn("encounterId", encountersNamingProvider));
		}
		if (applySortCriteria && searchCriteria.getSortCriteria() != null) {
			for (SortCriteria sortCriteria : searchCriteria.getSortCriteria()) {
				if (sortCriteria.getDirection() == SortCriteria.Direction.DESC) {
					c.addOrder(desc(sortCriteria.getField()));
				} else {
					c.addOrder(asc(sortCriteria.getField()));
				}
			}
		}
		return c;
	}

}
