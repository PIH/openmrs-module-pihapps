package org.openmrs.module.pihapps.rest;

import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.PihAppsService;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEvent;
import org.openmrs.module.pihapps.orders.OrderFulfillmentEventType;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validation state of lab orders' results, and validating or returning them for correction
 */
@Controller
public class LabResultValidationRestController {

    @Autowired
    OrderService orderService;

    @Autowired
    PihAppsService pihAppsService;

    /**
     * @return for each order, by uuid: its latest validation event, who entered its results, whether the current user
     * is one of them or may validate them, the history of its validation events (amendments included), and the uuids
     * of its current results, which a request to validate them sends back
     */
    @RequestMapping(value = "/rest/v1/pihapps/labResultValidation", method = RequestMethod.GET)
    @ResponseBody
    public Object getValidation(@RequestParam(value = "order") List<String> orderUuids) {
        requirePrivilege(PrivilegeConstants.GET_ORDERS);
        User user = Context.getAuthenticatedUser();
        SimpleObject ret = new SimpleObject();
        for (Order order : getOrders(orderUuids)) {
            List<String> enteredBy = new ArrayList<>();
            boolean enteredByUser = false;
            for (User editor : pihAppsService.getLabResultEditors(order)) {
                enteredBy.add(getName(editor));
                enteredByUser = enteredByUser || editor.equals(user);
            }
            List<SimpleObject> history = new ArrayList<>();
            OrderFulfillmentEvent latest = null;
            for (OrderFulfillmentEvent event : pihAppsService.getOrderFulfillmentEvents(order)) {
                if (OrderFulfillmentEventType.RESULT_VALIDATION.contains(event.getEventType())) {
                    history.add(toSimpleObject(event));
                    latest = event; // events are in chronological order
                }
                else if (event.getEventType() == OrderFulfillmentEventType.RESULTS_AMENDED) {
                    history.add(toSimpleObject(event));
                }
            }
            List<String> results = new ArrayList<>();
            for (Obs obs : pihAppsService.getLabResultObs(order)) {
                results.add(obs.getUuid());
            }
            SimpleObject o = new SimpleObject();
            o.put("eventType", latest == null ? null : latest.getEventType().name());
            o.put("comment", latest == null ? null : latest.getComment());
            o.put("enteredBy", enteredBy);
            o.put("enteredByCurrentUser", enteredByUser);
            o.put("canValidate", Context.hasPrivilege(LabOrderConfig.PRIVILEGE_VALIDATE_RESULTS)
                    && pihAppsService.canValidateLabResults(order, user));
            o.put("history", history);
            o.put("results", results);
            ret.put(order.getUuid(), o);
        }
        return ret;
    }

    @RequestMapping(value = "/rest/v1/pihapps/labResultValidation", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> decide(@RequestBody ValidationRequest request) {
        List<Order> orders = getOrders(request.getOrders());
        if (orders.isEmpty() || new HashSet<>(orders).size() != orders.size()) {
            return ResponseEntity.badRequest().body(error("Orders must be given, each once"));
        }
        if ("VALIDATE".equals(request.getAction())) {
            // The results reviewed, by order uuid: validation fails if they are no longer the order's results
            Map<Order, List<String>> reviewed = new HashMap<>();
            for (Order order : orders) {
                List<String> results = request.getResults() == null ? null : request.getResults().get(order.getUuid());
                if (results == null) {
                    return ResponseEntity.badRequest().body(error("The results reviewed are required for order " + order.getUuid()));
                }
                reviewed.put(order, results);
            }
            pihAppsService.validateLabResults(orders, reviewed, request.getComment());
        }
        else if ("RETURN".equals(request.getAction())) {
            pihAppsService.returnLabResultsForCorrection(orders, request.getComment());
        }
        else {
            return ResponseEntity.badRequest().body(error("Action must be VALIDATE or RETURN"));
        }
        return ResponseEntity.ok(new SimpleObject().add("success", true));
    }

    @ExceptionHandler(APIAuthenticationException.class)
    @ResponseBody
    public ResponseEntity<?> handleAuthenticationException(APIAuthenticationException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
    }

    @ExceptionHandler({ APIException.class, IllegalArgumentException.class })
    @ResponseBody
    public ResponseEntity<?> handleBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(error(e.getMessage()));
    }

    /**
     * @return the given event as {eventType, comment, eventDatetime, creator}, where creator is the user's display name
     */
    static SimpleObject toSimpleObject(OrderFulfillmentEvent event) {
        SimpleObject o = new SimpleObject();
        o.put("eventType", event.getEventType().name());
        o.put("comment", event.getComment());
        o.put("eventDatetime", ConversionUtil.convertToRepresentation(event.getEventDatetime(), Representation.DEFAULT));
        User creator = event.getCreator();
        o.put("creator", creator == null ? null : creator.getDisplayString());
        return o;
    }

    /**
     * @return the user's person name, else username, else display string
     */
    private String getName(User user) {
        if (user.getPersonName() != null) {
            return user.getPersonName().getFullName();
        }
        return StringUtils.isNotBlank(user.getUsername()) ? user.getUsername() : user.getDisplayString();
    }

    private List<Order> getOrders(List<String> orderUuids) {
        if (orderUuids == null) {
            return Collections.emptyList();
        }
        List<Order> ret = new ArrayList<>();
        for (String uuid : orderUuids) {
            Order order = orderService.getOrderByUuid(uuid);
            if (order == null) {
                throw new IllegalArgumentException("Order " + uuid + " not found");
            }
            ret.add(order);
        }
        return ret;
    }

    private void requirePrivilege(String privilege) {
        if (!Context.hasPrivilege(privilege)) {
            throw new APIAuthenticationException("Privilege required: " + privilege);
        }
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("success", false);
        ret.put("message", message);
        return ret;
    }

    @Data
    public static class ValidationRequest {
        private List<String> orders;
        private String action;
        private String comment;
        private Map<String, List<String>> results; // VALIDATE: the uuids of the results reviewed, by order uuid
    }
}
