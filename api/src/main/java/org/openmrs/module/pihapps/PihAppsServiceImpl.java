/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p>
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.pihapps;

import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.LocationService;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public class PihAppsServiceImpl extends BaseOpenmrsService implements PihAppsService {

	protected Log log = LogFactory.getLog(getClass());

	@Setter
	private LocationService locationService;

	@Override
	@Transactional
	@Authorized(PrivilegeConstants.MANAGE_LOCATIONS)
	public void updateVisitAndLoginLocations(List<Location> visitLocations, List<Location> loginLocations) {
		LocationTag visitLocationTag = locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_VISITS);
		LocationTag loginLocationTag = locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN);
		for (Location l : locationService.getAllLocations(true)) {
			boolean locationChanged = false;
			boolean isVisitLocation = l.getTags() != null && l.getTags().contains(visitLocationTag);
			boolean isLoginLocation = l.getTags() != null && l.getTags().contains(loginLocationTag);
			boolean shouldBeVisitLocation = visitLocations.contains(l);
			boolean shouldBeLoginLocation = loginLocations.contains(l);
			if (isVisitLocation && !shouldBeVisitLocation) {
				l.removeTag(visitLocationTag);
				locationChanged = true;
			}
			if (isLoginLocation && !shouldBeLoginLocation) {
				l.removeTag(loginLocationTag);
				locationChanged = true;
			}
			if (!isVisitLocation && shouldBeVisitLocation) {
				l.addTag(visitLocationTag);
				locationChanged = true;
			}
			if (!isLoginLocation && shouldBeLoginLocation) {
				l.addTag(loginLocationTag);
				locationChanged = true;
			}
			if (locationChanged) {
				locationService.saveLocation(l);
			}
		}
	}
}
