package org.openmrs.module.pihapps.labs;

import org.openmrs.Location;

/**
 * Extension point for auto-generating a Lab ID for a specimen collection encounter at a given Location
 */
public interface LabIdGenerator {

    /**
     *
     * @return true if this particular implementation of the Lab ID generator is enabled
     */
    boolean isEnabled(Location sessionLocation);

    /**
     * @param sessionLocation the user's current session/login location
     * @return a newly generated Lab ID
     * @throws RuntimeException if generation is not possible (e.g. missing configuration
     *         for the given location) - the exception's message is shown to the user
     */
    String generateLabId(Location sessionLocation);
}
