package org.openmrs.module.pihapps.htmlformentry.labs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openmrs.Concept;
import org.openmrs.module.htmlformentry.util.JsonObject;
import org.openmrs.module.pihapps.PihAppsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LabOrderWidgetTest {

	PihAppsUtils pihAppsUtils;

	@BeforeEach
	public void setup() {
		pihAppsUtils = mock(PihAppsUtils.class);
		when(pihAppsUtils.getBestShortName(Mockito.any())).thenAnswer(i -> "short-" + ((Concept) i.getArgument(0)).getId());
		when(pihAppsUtils.formatLabTest(Mockito.any())).thenAnswer(i -> "display-" + ((Concept) i.getArgument(0)).getId());
	}

	@Test
	public void addLabTestCategories_shouldOnlyIncludeConfiguredTestsInEachCategory() {
		Concept test1 = concept(1);
		Concept test2 = concept(2);
		Concept test3 = concept(3);
		Concept category = concept(10, test1, test2, test3);

		LabOrderWidgetConfig widgetConfig = new LabOrderWidgetConfig();
		widgetConfig.getOrderablesByCategory().put(category, new ArrayList<>(Arrays.asList(test1, test3)));

		JsonObject config = new JsonObject();
		LabOrderWidget.addLabTestCategories(config, widgetConfig, pihAppsUtils);

		List<JsonObject> categories = config.getObjectArray("labTestCategories");
		assertThat(categories, hasSize(1));
		assertThat(categories.get(0).getString("conceptId"), equalTo("10"));
		assertThat(conceptIds(categories.get(0).getObjectArray("labTests")), contains("1", "3"));
	}

	@Test
	public void addLabTestCategories_shouldIncludeAllTestsInPanelAndReasons() {
		Concept testInPanel1 = concept(1);
		Concept testInPanel2 = concept(2);
		Concept panel = concept(3, testInPanel1, testInPanel2);
		Concept reason = concept(20);
		Concept category = concept(10, panel, testInPanel1, testInPanel2);

		LabOrderWidgetConfig widgetConfig = new LabOrderWidgetConfig();
		widgetConfig.getOrderablesByCategory().put(category, new ArrayList<>(Arrays.asList(panel)));
		widgetConfig.getOrderReasonsByOrderable().put(panel, new ArrayList<>(Arrays.asList(reason)));

		JsonObject config = new JsonObject();
		LabOrderWidget.addLabTestCategories(config, widgetConfig, pihAppsUtils);

		List<JsonObject> labTests = config.getObjectArray("labTestCategories").get(0).getObjectArray("labTests");
		assertThat(conceptIds(labTests), contains("3"));
		JsonObject panelJson = labTests.get(0);
		assertThat(panelJson.getString("isPanel"), equalTo("true"));
		assertThat(panelJson.getString("shortName"), equalTo("short-3"));
		assertThat(panelJson.getString("displayName"), equalTo("display-3"));
		assertThat(conceptIds(panelJson.getObjectArray("testsInPanel")), contains("1", "2"));
		assertThat(conceptIds(panelJson.getObjectArray("reasons")), contains("20"));
	}

	private Concept concept(int id, Concept... setMembers) {
		Concept c = new Concept(id);
		c.setSet(setMembers.length > 0);
		for (Concept member : setMembers) {
			c.addSetMember(member);
		}
		return c;
	}

	private List<String> conceptIds(List<JsonObject> objects) {
		return objects.stream().map(o -> o.getString("conceptId")).collect(Collectors.toList());
	}
}
