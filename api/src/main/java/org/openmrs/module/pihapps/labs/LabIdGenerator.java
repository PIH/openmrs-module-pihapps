package org.openmrs.module.pihapps.labs;

import org.openmrs.Location;

/**
 * Extension point for auto-generating a Lab ID for a specimen collection encounter.
 * An implementation is registered as a Spring bean by any module that wants to enable
 * the "Generate" affordance in the specimen collection UI. The presence of a registered
 * bean is itself the feature toggle - see LabOrderConfig#isLabIdAutoGenerationEnabled().
 */
public interface LabIdGenerator {

    /**
     * @param sessionLocation the user's current session/login location
     * @return a newly generated Lab ID
     * @throws RuntimeException if generation is not possible (e.g. missing configuration
     *         for the given location) - the exception's message is shown to the user
     */
    String generateLabId(Location sessionLocation);
}
