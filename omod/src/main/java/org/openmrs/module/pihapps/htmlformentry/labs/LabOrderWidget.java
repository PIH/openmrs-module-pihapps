package org.openmrs.module.pihapps.htmlformentry.labs;

import org.apache.commons.lang.BooleanUtils;
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.util.JsonObject;
import org.openmrs.module.htmlformentry.widget.OrderWidget;
import org.openmrs.module.htmlformentry.widget.OrderWidgetConfig;
import org.openmrs.module.pihapps.PihAppsUtils;

import java.util.List;
import java.util.Map;

public class LabOrderWidget extends OrderWidget {

	public LabOrderWidget(FormEntryContext context, OrderWidgetConfig widgetConfig) {
		super(context, widgetConfig);
	}

	@Override
	public JsonObject constructJavascriptConfig(FormEntryContext context) {
		JsonObject config = super.constructJavascriptConfig(context);
		if (getWidgetConfig() instanceof LabOrderWidgetConfig) {
			addLabTestCategories(config, (LabOrderWidgetConfig) getWidgetConfig(), getPihAppsUtils());

			// Add additional translations needed
			config.getObject("translations").addTranslation("pihapps.", "testsIncludedInThisPanel");
		}
		return config;
	}

	/**
	 * Adds the orderable lab tests by category to the javascript config.  Only the tests in the widget config are
	 * included, as these have been filtered to those that are enabled and configured in the tag.
	 */
	static void addLabTestCategories(JsonObject config, LabOrderWidgetConfig widgetConfig, PihAppsUtils pihAppsUtils) {
		for (Map.Entry<Concept, List<Concept>> categoryEntry : widgetConfig.getOrderablesByCategory().entrySet()) {

			// Add category to "labTestCategories" array in JSON
			JsonObject categoryObject = config.addObjectToArray("labTestCategories");
			populateJsonConcept(categoryObject, categoryEntry.getKey(), pihAppsUtils);

			for (Concept test : categoryEntry.getValue()) {
				JsonObject testObject = categoryObject.addObjectToArray("labTests");
				populateJsonConcept(testObject, test, pihAppsUtils);
				boolean isSet = BooleanUtils.isTrue(test.getSet());
				testObject.addString("isPanel", isSet ? "true" : "false");
				if (isSet) {
					for (Concept testInPanel : test.getSetMembers()) {
						JsonObject testInPanelObject = testObject.addObjectToArray("testsInPanel");
						populateJsonConcept(testInPanelObject, testInPanel, pihAppsUtils);
					}
				}
				List<Concept> reasons = widgetConfig.getOrderReasonsByOrderable().get(test);
				if (reasons != null) {
					for (Concept reason : reasons) {
						JsonObject reasonObject = testObject.addObjectToArray("reasons");
						populateJsonConcept(reasonObject, reason, pihAppsUtils);
					}
				}
			}
		}
	}

	static void populateJsonConcept(JsonObject jsonConcept, Concept concept, PihAppsUtils pihAppsUtils) {
		jsonConcept.addString("conceptId", concept.getId().toString());
		jsonConcept.addString("conceptUuid", concept.getUuid());
		jsonConcept.addString("shortName", pihAppsUtils.getBestShortName(concept));
		jsonConcept.addString("displayName", pihAppsUtils.formatLabTest(concept));
	}

	PihAppsUtils getPihAppsUtils() {
		return Context.getRegisteredComponents(PihAppsUtils.class).get(0);
	}
}
