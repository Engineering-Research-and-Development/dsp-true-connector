package it.eng.dcp.common.participant;

/**
 * Issuer metadata configuration of a participant that acts as a Credential Issuer.
 *
 * @param issuerLocation the location URL the issuer advertises, may be {@code null} when not configured
 */
public record IssuerMetadataConfiguration(String issuerLocation) {
}
