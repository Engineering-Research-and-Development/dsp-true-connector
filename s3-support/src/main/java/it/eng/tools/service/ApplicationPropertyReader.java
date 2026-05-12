package it.eng.tools.service;

import java.util.Optional;

/**
 * Minimal interface for reading a single application property value by key.
 *
 * <p>In the Control Plane context this is implemented by
 * {@code ApplicationPropertiesService}, enabling S3 upload-mode overrides via
 * MongoDB-backed properties. In the Data Plane context no implementation is
 * available and the S3 client falls back to the {@code s3.upload-mode}
 * application property.
 */
public interface ApplicationPropertyReader {

    /**
     * Returns the value of the property with the given key, if present.
     *
     * @param key the property key to look up
     * @return an {@link Optional} containing the value, or empty if not found
     */
    Optional<String> getPropertyValue(String key);
}
