package it.eng.dataplane.s3.model;

/**
 * Data Plane constants for data address keys used in transfer flows.
 */
public final class IConstants {

    private IConstants() {
    }

    /** Key for the authorization type (e.g. {@code Bearer}, {@code Basic}). */
    public static final String AUTH_TYPE = "authType";

    /** Key for the authorization token value. */
    public static final String AUTHORIZATION = "authorization";
}
