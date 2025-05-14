package it.eng.tools.util;

import java.util.UUID;

public class ToolsUtil {

    /**
     * Generates a unique identifier in the format "urn:uuid:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx".
     *
     * @return A unique identifier string.
     */
    public static String generateUniqueId() {
        return "urn:uuid:" + UUID.randomUUID().toString();
    }
}
