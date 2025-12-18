package it.eng.dcp.core;

import it.eng.dcp.common.model.ProfileId;

import java.util.Map;

/**
 * Resolve the profile for a given credential/presentation input.
 * The resolver can look at format and presence of statusList or other metadata to pick a ProfileId.
 */
public interface ProfileResolver {

    /**
     * Determine a ProfileId based on the provided attributes.
     * @param format e.g. "jwt" or "json-ld"
     * @param attributes additional attributes such as `statusList` presence
     * @return matching ProfileId or null if none matches
     */
    ProfileId resolve(String format, Map<String, Object> attributes);
}

