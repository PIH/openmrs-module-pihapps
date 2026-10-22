package org.openmrs.module.pihapps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Covers identifying admission locations by the "Admission Location" tag
 */
public class LocationTagConfigTest extends BaseModuleContextSensitiveTest {

    private LocationService locationService;

    private LocationTagConfig locationTagConfig;

    @BeforeEach
    public void setup() {
        locationService = Context.getLocationService();
        locationTagConfig = new LocationTagConfig();
        locationTagConfig.setLocationService(locationService);
    }

    private Location createLocation(String name, LocationTag tag) {
        Location location = new Location();
        location.setName(name);
        if (tag != null) {
            location.addTag(tag);
        }
        return locationService.saveLocation(location);
    }

    @Test
    public void shouldIdentifyLocationWithAdmissionTagAsAdmissionLocation() {
        LocationTag admissionTag = locationService.saveLocationTag(new LocationTag(EmrApiConstants.LOCATION_TAG_SUPPORTS_ADMISSION, "Admission"));
        Location ward = createLocation("Test Ward", admissionTag);
        assertThat(locationTagConfig.isAdmissionLocation(ward), is(true));
    }

    @Test
    public void shouldNotIdentifyLocationWithoutAdmissionTagAsAdmissionLocation() {
        LocationTag admissionTag = locationService.saveLocationTag(new LocationTag(EmrApiConstants.LOCATION_TAG_SUPPORTS_ADMISSION, "Admission"));
        LocationTag otherTag = locationService.saveLocationTag(new LocationTag(EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN, "Login"));
        assertThat(locationTagConfig.isAdmissionLocation(createLocation("Test Lab", otherTag)), is(false));
        assertThat(locationTagConfig.isAdmissionLocation(createLocation("Test Untagged", null)), is(false));
    }

    @Test
    public void shouldNotIdentifyNullLocationAsAdmissionLocation() {
        assertThat(locationTagConfig.isAdmissionLocation(null), is(false));
    }

    @Test
    public void shouldNotIdentifyAnyLocationAsAdmissionLocationWhenTagDoesNotExist() {
        assertThat(locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_ADMISSION), is(nullValue()));
        LocationTag otherTag = locationService.saveLocationTag(new LocationTag(EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN, "Login"));
        assertThat(locationTagConfig.isAdmissionLocation(createLocation("Test Ward", otherTag)), is(false));
    }
}
