package org.openmrs.module.pihapps.rest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.SortCriteria;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.module.pihapps.orders.OrderSearchCriteria;
import org.openmrs.module.pihapps.orders.OrderSearchResult;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.Converter;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Provides configuration and other endpoints for lab order entry
 */
@Controller
public class LabOrderRestController {

    protected final Log log = LogFactory.getLog(getClass());

    private final PihAppsService pihAppsService;

    private final LabOrderConfig labOrderConfig;

    @Autowired
    public LabOrderRestController(PihAppsService pihAppsService, LabOrderConfig labOrderConfig) {
        this.pihAppsService = pihAppsService;
        this.labOrderConfig = labOrderConfig;
    }

    @RequestMapping(value = "/rest/v1/pihapps/labOrder", method = RequestMethod.GET)
    @ResponseBody
    public Object getLabOrders(HttpServletRequest request, HttpServletResponse response,
                               @RequestParam(value = "patient", required = false) Patient patient,
                               @RequestParam(value = "labTest", required = false) Concept labTest,
                               @RequestParam(value = "orderType", required = false) List<OrderType> orderType,
                               @RequestParam(value = "activatedOnOrBefore", required = false) String activatedOnOrBefore,
                               @RequestParam(value = "activatedOnOrAfter", required = false) String activatedOnOrAfter,
                               @RequestParam(value = "accessionNumber", required = false) String accessionNumber,
                               @RequestParam(value = "orderFulfillmentStatus", required = false) OrderFulfillmentStatus orderFulfillmentStatus,
                               @RequestParam(value = "sortBy", required = false) List<String> sortBy
                               ) throws ResponseException {

        RequestContext requestContext = RestUtil.getRequestContext(request, response, Representation.REF);
        Integer startIndex = requestContext.getStartIndex() == null ? 0 : requestContext.getStartIndex();
        Integer limit = requestContext.getLimit();

        OrderSearchCriteria searchCriteria = new OrderSearchCriteria();
        searchCriteria.setOrderTypes(orderType == null ? labOrderConfig.getTestOrderTypes() : orderType);
        searchCriteria.setPatient(patient);
        searchCriteria.setConcept(labTest);
        searchCriteria.setAccessionNumber(accessionNumber);
        searchCriteria.setActivatedOnOrBefore(getDate(activatedOnOrBefore));
        searchCriteria.setActivatedOnOrAfter(getDate(activatedOnOrAfter));
        if (orderFulfillmentStatus != null) {
            if (orderFulfillmentStatus.getOrderStatus() != null) {
                searchCriteria.setOrderStatus(Collections.singletonList(orderFulfillmentStatus.getOrderStatus()));
            }
            searchCriteria.setFulfillerStatuses(orderFulfillmentStatus.getFulfillerStatuses());
            searchCriteria.setIncludeNullFulfillerStatus(orderFulfillmentStatus.getIncludeNullFulfillerStatus());
        }
        searchCriteria.setStartIndex(requestContext.getStartIndex());
        searchCriteria.setLimit(requestContext.getLimit());

        List<SortCriteria> sortCriteriaList = new ArrayList<>();
        if (sortBy != null && !sortBy.isEmpty()) {
            for (String sortByValue : sortBy) {
                if (StringUtils.isNotBlank(sortByValue)) {
                    String[] components = sortByValue.split("-", 2);
                    String field = components[0];
                    SortCriteria.Direction direction = SortCriteria.Direction.ASC;
                    if (components.length > 1) {
                        direction = SortCriteria.Direction.valueOf(components[1].toUpperCase());
                    }
                    sortCriteriaList.add(new SortCriteria(field, direction));
                }
            }
        }
        if (!sortCriteriaList.isEmpty()) {
            searchCriteria.setSortCriteria(sortCriteriaList);
        }

        OrderSearchResult result = pihAppsService.getOrders(searchCriteria);

        boolean hasMoreResults = false;
        if (limit != null) {
            int recordsProcessed = startIndex + limit + 1;
            hasMoreResults = recordsProcessed < result.getTotalCount();
        }

        Converter<Order> orderConverter = ConversionUtil.getConverter(Order.class);
        AlreadyPaged<Order> alreadyPaged = new AlreadyPaged<>(requestContext, result.getOrders(), hasMoreResults, result.getTotalCount());
        return alreadyPaged.toSimpleObject(orderConverter);
    }

    Date getDate(String ymd) {
        if (StringUtils.isBlank(ymd)) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(ymd);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Could not parse date: " + ymd);
        }
    }
}