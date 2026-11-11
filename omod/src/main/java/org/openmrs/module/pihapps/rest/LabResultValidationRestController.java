package org.openmrs.module.pihapps.rest;

import lombok.Data;
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
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Collections;
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
	 * is one of them or may validate them, and the history of its validation events
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
				enteredBy.add(editor.getPersonName() != null ? editor.getPersonName().getFullName() : editor.getUsername());
				enteredByUser = enteredByUser || editor.equals(user);
			}
			List<SimpleObject> history = new ArrayList<>();
			OrderFulfillmentEvent latest = null;
			for (OrderFulfillmentEvent event : pihAppsService.getOrderFulfillmentEvents(order)) {
				if (OrderFulfillmentEventType.RESULT_VALIDATION.contains(event.getEventType())) {
					history.add(toSimpleObject(event));
					latest = event; // events are in chronological order
				}
			}
			SimpleObject o = new SimpleObject();
			o.put("eventType", latest == null ? null : latest.getEventType().name());
			o.put("comment", latest == null ? null : latest.getComment());
			o.put("enteredBy", enteredBy);
			o.put("enteredByCurrentUser", enteredByUser);
			o.put("canValidate", pihAppsService.canValidateLabResults(order, user));
			o.put("history", history);
			ret.put(order.getUuid(), o);
		}
		return ret;
	}

	@RequestMapping(value = "/rest/v1/pihapps/labResultValidation", method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> decide(@RequestBody ValidationRequest request) {
		try {
			List<Order> orders = getOrders(request.getOrders());
			if ("VALIDATE".equals(request.getAction())) {
				pihAppsService.validateLabResults(orders, request.getComment());
			}
			else if ("RETURN".equals(request.getAction())) {
				pihAppsService.returnLabResultsForCorrection(orders, request.getComment());
			}
			else {
				return ResponseEntity.badRequest().body(error("Action must be VALIDATE or RETURN"));
			}
			return ResponseEntity.ok(new SimpleObject().add("success", true));
		}
		catch (APIAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
		}
		catch (APIException | IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(error(e.getMessage()));
		}
	}

	/**
	 * @return the given event as {eventType, comment, eventDatetime, creator}, where creator is the user's display name
	 */
	static SimpleObject toSimpleObject(OrderFulfillmentEvent event) {
		SimpleObject o = new SimpleObject();
		o.put("eventType", event.getEventType().name());
		o.put("comment", event.getComment());
		o.put("eventDatetime", event.getEventDatetime());
		User creator = event.getCreator();
		o.put("creator", creator == null ? null : creator.getDisplayString());
		return o;
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
	}
}
