package org.openmrs.module.pihapps.htmlformentry.labs;

import org.apache.commons.lang.BooleanUtils;
import org.openmrs.Concept;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.util.JsonObject;
import org.openmrs.module.htmlformentry.widget.OrderWidget;
import org.openmrs.module.htmlformentry.widget.OrderWidgetConfig;
import org.openmrs.module.pihapps.PihAppsUtils;

import java.util.List;

public class LabOrderWidget extends OrderWidget {

	private final PihAppsUtils pihAppsUtils = new PihAppsUtils();

	public LabOrderWidget(FormEntryContext context, OrderWidgetConfig widgetConfig) {
		super(context, widgetConfig);
	}

	@Override
	public JsonObject constructJavascriptConfig(FormEntryContext context) {
		JsonObject config = super.constructJavascriptConfig(context);
		if (getWidgetConfig() instanceof LabOrderWidgetConfig) {
			LabOrderWidgetConfig widgetConfig = (LabOrderWidgetConfig) getWidgetConfig();
			for (Concept category : widgetConfig.getOrderablesByCategory().keySet()) {

				// Add category to "labTestCategories" array in JSON
				JsonObject categoryObject = config.addObjectToArray("labTestCategories");
				populateJsonConcept(categoryObject, category);

				for (Concept test : category.getSetMembers()) {
					JsonObject testObject = categoryObject.addObjectToArray("labTests");
					populateJsonConcept(testObject, test);
					boolean isSet = BooleanUtils.isTrue(test.getSet());
					testObject.addString("isPanel", isSet ? "true" : "false");
					if (isSet) {
						for (Concept testInPanel : test.getSetMembers()) {
							JsonObject testInPanelObject = testObject.addObjectToArray("testsInPanel");
							populateJsonConcept(testInPanelObject, testInPanel);
						}
					}
					List<Concept> reasons = widgetConfig.getOrderReasonsByOrderable().get(test);
					if (reasons != null) {
						for (Concept reason : reasons) {
							JsonObject reasonObject = testObject.addObjectToArray("reasons");
							populateJsonConcept(reasonObject, reason);
						}
					}
				}
			}
		}
		return config;
	}

	void populateJsonConcept(JsonObject jsonConcept, Concept concept) {
		jsonConcept.addString("conceptId", concept.getId().toString());
		jsonConcept.addString("conceptUuid", concept.getUuid());
		jsonConcept.addString("shortName", pihAppsUtils.getBestShortName(concept));
		jsonConcept.addString("displayName", concept.getDisplayString());
	}
}
