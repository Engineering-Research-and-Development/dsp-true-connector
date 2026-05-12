package it.eng.dataplane.s3.model;

/**
 * Data Plane constants for data address keys used in transfer flows.
 */
public interface IConstants {

    /** Key for the authorization type (e.g. {@code Bearer}, {@code Basic}). */
    String AUTH_TYPE = "authType";

    /** Key for the authorization token value. */
    String AUTHORIZATION = "authorization";
}
