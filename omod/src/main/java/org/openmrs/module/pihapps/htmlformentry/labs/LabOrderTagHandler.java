package org.openmrs.module.pihapps.htmlformentry.labs;

import org.apache.commons.lang.StringUtils;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.module.htmlformentry.BadFormDesignException;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.htmlformentry.handler.OrderTagHandler;
import org.openmrs.module.htmlformentry.widget.Option;
import org.openmrs.module.htmlformentry.widget.OrderWidget;
import org.openmrs.module.htmlformentry.widget.OrderWidgetConfig;
import org.openmrs.module.pihapps.LabOrderConfig;
import org.openmrs.module.pihapps.PihAppsUtils;
import org.openmrs.ui.framework.UiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles the {@code <pihLabOrder>} tag to configure lab orders in accordance with lab order pages and configuration
 */
@Component
public class LabOrderTagHandler extends OrderTagHandler {

	public static final String TAG_NAME = "pihLabOrder";

	final PihAppsUtils pihAppsUtils = new PihAppsUtils();

	@Autowired
	LabOrderConfig labOrderConfig;

	@Override
	public boolean doStartTag(FormEntrySession session, PrintWriter out, Node p, Node node) throws BadFormDesignException {
		UiUtils uiUtils = (UiUtils) session.getAttribute("uiUtils");
		if (uiUtils == null) {
			throw new IllegalArgumentException("uiUtils is not found as expected, please ensure htmlformentryui is installed");
		}
		uiUtils.includeJavascript("pihapps", "labs/renderLabOrdersByCategory.js");
		uiUtils.includeCss("pihapps", "labs/renderLabOrdersByCategory.css");
		return super.doStartTag(session, out, p, node);
	}

	@Override
	protected OrderWidgetConfig createOrderWidgetConfig() {
		LabOrderWidgetConfig widgetConfig = new LabOrderWidgetConfig();
		String orderType = widgetConfig.getAttribute("orderType");
		if (StringUtils.isBlank(orderType)) {
			widgetConfig.getAttributes().put("orderType", OrderType.TEST_ORDER_TYPE_UUID);
		}
		String onLoadFunction = widgetConfig.getAttribute("onLoadFunction");
		if (StringUtils.isBlank(onLoadFunction)) {
			widgetConfig.getAttributes().put("onLoadFunction", "renderLabOrdersByCategory");
		}
		return widgetConfig;
	}

	@Override
	protected OrderWidget createOrderWidget(FormEntryContext context, OrderWidgetConfig widgetConfig) {
		return new LabOrderWidget(context, widgetConfig);
	}

	/**
	 * If no care settings are explicitly configured, set up the widget to use the default care setting for lab
	 * which is defined as the first found OUTPATIENT care setting, or the first found care setting if none
	 */
	@Override
	protected void processCareSettingOptions(OrderWidgetConfig config) throws BadFormDesignException {
		List<Option> options = config.getOrderPropertyOptions("careSetting");
		if (options.isEmpty()) {
			CareSetting careSetting = labOrderConfig.getDefaultCareSetting();
			options.add(new Option(careSetting.getName(), careSetting.getCareSettingId().toString(), true));
		}
		super.processCareSettingOptions(config);
	}

	/**
	 * If no urgencies are explicitly configured, set up the widget support NORMAL and STAT
	 */
	@Override
	protected void processEnumOptions(OrderWidgetConfig config, String property, Enum[] vals, Enum defVal) {
		if ("urgency".equals(property)) {
			List<Option> options = config.getOrderPropertyOptions("urgency");
			if (options.isEmpty()) {
				options.add(new Option(HtmlFormEntryUtil.translate("htmlformentry.orders.urgency.routine"), Order.Urgency.ROUTINE.name(), true));
				options.add(new Option(HtmlFormEntryUtil.translate("htmlformentry.orders.urgency.stat"), Order.Urgency.STAT.name(), false));
			}
		}
		else if ("action".equals(property)) {
			List<Option> options = config.getOrderPropertyOptions("action");
			if (options.isEmpty()) {
				for (Order.Action action : Order.Action.values()) {
					options.add(new Option(HtmlFormEntryUtil.translate("htmlformentry.orders.action." + action.name().toLowerCase()), action.name()));
				}
			}
			else {
				throw new IllegalArgumentException("Action options cannot be customized with the LabOrderTag");
			}
		}
		super.processEnumOptions(config, property, vals, defVal);
	}

	/**
	 * If no concepts are explicitly configured, then populate with all lab orderables
	 * If concepts _are_ explicitly configured, then populate with lab orderables that are configured
	 * Also add the appropriate configuration to the widget config for the categories
	 */
	@Override
	protected void processConceptOptions(OrderWidgetConfig config, String prop) throws BadFormDesignException {
		super.processConceptOptions(config, prop);
		if ("concept".equals(prop)) {
			processLabTestOptions(config, prop);
		}
	}

	protected void processLabTestOptions(OrderWidgetConfig config, String prop) throws BadFormDesignException {
		LabOrderWidgetConfig labOrderWidgetConfig = (LabOrderWidgetConfig) config;
		Map<Concept, List<Concept>> labOrderables = labOrderConfig.getAvailableLabTestsByCategory();
		Map<Concept, List<Concept>> orderReasonMap = labOrderConfig.getOrderReasonsMap();

		Set<Concept> conceptsConfigured = new HashSet<>(config.getConceptsAndDrugsConfigured().keySet());
		List<Option> orderPropertyOptions = config.getOrderPropertyOptions("concept");
		boolean conceptsAreConfigured = !orderPropertyOptions.isEmpty();

		for (Concept category : labOrderables.keySet()) {
			for (Concept orderable : labOrderables.get(category)) {
				boolean supported = !conceptsAreConfigured || conceptsConfigured.remove(orderable);
				if (supported) {
					if (!conceptsAreConfigured) {
						Option option = new Option();
						option.setLabel(pihAppsUtils.getBestShortName(orderable));
						option.setValue(orderable.getConceptId().toString());
						orderPropertyOptions.add(option);
						config.getConceptsAndDrugsConfigured().put(orderable, new ArrayList<>());
					}
					labOrderWidgetConfig.getOrderablesByCategory().computeIfAbsent(category, k -> new ArrayList<>()).add(orderable);
					labOrderWidgetConfig.getOrderReasonsByOrderable().put(orderable, orderReasonMap.getOrDefault(orderable, new ArrayList<>()));
				}
			}
		}
		// If any concepts were configured and not removed while iterating over the supported orderables, throw error
		if (!conceptsConfigured.isEmpty()) {
			throw new BadFormDesignException("Labs " + conceptsConfigured + " are not contained within " + labOrderConfig.getLabOrderablesConceptSet());
		}
	}
}
