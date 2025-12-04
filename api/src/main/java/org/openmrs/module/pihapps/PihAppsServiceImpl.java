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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Projections;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Order;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.LocationService;
import org.openmrs.api.OrderService;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.module.pihapps.orders.OrderStatus;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
	private LocationService locationService;

	@Setter
	private OrderService orderService;

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
	@SuppressWarnings({ "unchecked", "deprecation" })
	public OrderSearchResult getOrders(OrderSearchCriteria searchCriteria) {
		OrderSearchResult result = new OrderSearchResult();
		result.setCriteria(searchCriteria);

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

		c.setProjection(Projections.rowCount());
		Long totalCount = (Long) c.list().get(0);
		result.setTotalCount(totalCount);

		c.setProjection(null);

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
				c.addOrder(org.hibernate.criterion.Order.desc(sortCriteria.getField()));
			}
			else {
				c.addOrder(org.hibernate.criterion.Order.asc(sortCriteria.getField()));
			}
		}

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
}
