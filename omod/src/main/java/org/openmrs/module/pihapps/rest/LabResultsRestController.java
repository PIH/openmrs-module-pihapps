package org.openmrs.module.pihapps.rest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.SortCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchCriteria;
import org.openmrs.module.pihapps.obs.ObsSearchResult;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
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
 * Retrieves lab result observations
 */
@Controller
public class LabResultsRestController {

    protected final Log log = LogFactory.getLog(getClass());

    private final PihAppsService pihAppsService;

    private final LabOrderConfig labOrderConfig;

    private final OrderService orderService;

    private final ConceptService conceptService;

    @Autowired
    public LabResultsRestController(PihAppsService pihAppsService, LabOrderConfig labOrderConfig, OrderService orderService, ConceptService conceptService) {
        this.pihAppsService = pihAppsService;
        this.labOrderConfig = labOrderConfig;
        this.orderService = orderService;
        this.conceptService = conceptService;
    }

    @RequestMapping(value = "/rest/v1/pihapps/labResults", method = RequestMethod.GET)
    @ResponseBody
    public Object getLabResults(HttpServletRequest request, HttpServletResponse response,
                               @RequestParam(value = "patient", required = false) Patient patient,
                               @RequestParam(value = "labTest", required = false) Concept labTest,
                               @RequestParam(value = "category", required = false) Concept category,
                               @RequestParam(value = "onOrBefore", required = false) String onOrBefore,
                               @RequestParam(value = "onOrAfter", required = false) String onOrAfter,
                                @RequestParam(value = "sortBy", required = false) List<String> sortBy
                               ) throws ResponseException {

        RequestContext requestContext = RestUtil.getRequestContext(request, response, Representation.REF);
        Integer startIndex = requestContext.getStartIndex() == null ? 0 : requestContext.getStartIndex();
        Integer limit = requestContext.getLimit();

        ObsSearchCriteria searchCriteria = new ObsSearchCriteria();
        searchCriteria.setPatient(patient);

        List<Concept> concepts = null;
        if (labTest != null || category != null) {
            if (labTest != null) {
                concepts = getSetMembersRecursively(labTest);
                if (concepts.isEmpty()) {
                    concepts = Collections.singletonList(labTest);
                }
            }
            if (category != null) {
                List<Concept> categoryConcepts = getSetMembersRecursively(category);
                if (concepts != null) {
                    concepts.retainAll(categoryConcepts);
                }
                else {
                    concepts = categoryConcepts;
                }
            }
        }
        else {
            concepts = getSetMembersRecursively(labOrderConfig.getLabResultCategoriesConceptSet());
        }

        searchCriteria.setConcepts(concepts);
        searchCriteria.setOnOrBefore(getDate(onOrBefore));
        searchCriteria.setOnOrAfter(getDate(onOrAfter));
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
        if (sortCriteriaList.isEmpty()) {
            sortCriteriaList.add(new SortCriteria("obsDatetime", SortCriteria.Direction.DESC));
            sortCriteriaList.add(new SortCriteria("obsId", SortCriteria.Direction.DESC));
        }
        searchCriteria.setSortCriteria(sortCriteriaList);

        ObsSearchResult result = pihAppsService.getObs(searchCriteria);

        boolean hasMoreResults = false;
        if (limit != null) {
            int recordsProcessed = startIndex + limit + 1;
            hasMoreResults = recordsProcessed < result.getTotalCount();
        }

        Converter<Obs> obsConverter = ConversionUtil.getConverter(Obs.class);
        AlreadyPaged<Obs> alreadyPaged = new AlreadyPaged<>(requestContext, result.getObs(), hasMoreResults, result.getTotalCount());
        return alreadyPaged.toSimpleObject(obsConverter);
    }

    List<Concept> getSetMembersRecursively(Concept concept) {
        List<Concept> ret = new ArrayList<>();
        if (concept != null) {
            if (concept.getSetMembers() != null) {
                for (Concept setMember : concept.getSetMembers()) {
                    if (setMember.getSetMembers() != null && !setMember.getSetMembers().isEmpty()) {
                        ret.addAll(getSetMembersRecursively(setMember));
                    } else {
                        ret.add(setMember);
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Utility method to get a date from a string in yyyy-MM-dd format
     */
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