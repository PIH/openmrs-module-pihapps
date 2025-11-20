package org.openmrs.module.pihapps.rest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.labs.LabOrderSearchCriteria;
import org.openmrs.module.pihapps.labs.LabOrderSearchResult;
import org.openmrs.module.pihapps.labs.OrderStatus;
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
import java.util.Date;
import java.util.List;

/**
 * Provides configuration and other endpoints for lab order entry
 */
@Controller
public class LabOrderRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    PihAppsService pihAppsService;

    @RequestMapping(value = "/rest/v1/pihapps/labOrder", method = RequestMethod.GET)
    @ResponseBody
    public Object getLabOrders(HttpServletRequest request, HttpServletResponse response,
                               @RequestParam(value = "patient", required = false) Patient patient,
                               @RequestParam(value = "labTest", required = false) Concept labTest,
                               @RequestParam(value = "activatedOnOrBefore", required = false) String activatedOnOrBefore,
                               @RequestParam(value = "activatedOnOrAfter", required = false) String activatedOnOrAfter,
                               @RequestParam(value = "accessionNumber", required = false) String accessionNumber,
                               @RequestParam(value = "orderStatus", required = false) List<OrderStatus> orderStatus,
                               @RequestParam(value = "fulfillerStatus", required = false) List<Order.FulfillerStatus> fulfillerStatus
                               ) throws ResponseException {

        RequestContext requestContext = RestUtil.getRequestContext(request, response, Representation.REF);
        Integer startIndex = requestContext.getStartIndex() == null ? 0 : requestContext.getStartIndex();
        Integer limit = requestContext.getLimit();

        LabOrderSearchCriteria searchCriteria = new LabOrderSearchCriteria();
        searchCriteria.setPatient(patient);
        searchCriteria.setLabTest(labTest);
        searchCriteria.setAccessionNumber(accessionNumber);
        searchCriteria.setActivatedOnOrBefore(getDate(activatedOnOrBefore));
        searchCriteria.setActivatedOnOrAfter(getDate(activatedOnOrAfter));
        searchCriteria.setOrderStatus(orderStatus);
        searchCriteria.setFulfillerStatuses(fulfillerStatus);
        searchCriteria.setStartIndex(requestContext.getStartIndex());
        searchCriteria.setLimit(requestContext.getLimit());

        LabOrderSearchResult result = pihAppsService.getLabOrders(searchCriteria);

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