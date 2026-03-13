package it.eng.dcp.common.audit;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enumeration of all DCP audit event types.
 *
 * <p>Each constant carries a human-readable label that is used as the serialized
 * JSON value (via {@link JsonValue}) and as the lookup key in
 * {@link #fromLabel(String)}.
 *
 * <p>Grouped by concern:
 * <ul>
 *   <li>Credential issuance (issuer-side)</li>
 *   <li>Credential lifecycle (holder-side)</li>
 *   <li>Presentation exchange (holder-side)</li>
 *   <li>Presentation exchange (verifier-side)</li>
 *   <li>Token / Identity</li>
 *   <li>Key management</li>
 * </ul>
 */
public enum DcpAuditEventType {

    // -------------------------------------------------------------------------
    // Credential issuance — issuer-side
    // -------------------------------------------------------------------------

    /** A holder submitted a credential request that was received and stored. */
    CREDENTIAL_REQUEST_RECEIVED("Credential request received"),

    /** An operator approved a pending credential request; credentials were queued for delivery. */
    CREDENTIAL_APPROVED("Credential approved"),

    /** An operator rejected a pending credential request. */
    CREDENTIAL_DENIED("Credential denied"),

    /** The issuer successfully delivered credentials to the holder's Credential Service. */
    CREDENTIAL_DELIVERED("Credential delivered"),

    /** Credential delivery to the holder's Credential Service failed. */
    CREDENTIAL_DELIVERY_FAILED("Credential delivery failed"),

    /** A previously issued credential was revoked via the status list. */
    CREDENTIAL_REVOKED("Credential revoked"),

    // -------------------------------------------------------------------------
    // Credential lifecycle — holder-side
    // -------------------------------------------------------------------------

    /** The holder initiated a credential request to an issuer. */
    CREDENTIAL_REQUESTED("Credential requested"),

    /** A credential request to the issuer failed — non-2xx response or network/IO error. */
    CREDENTIAL_REQUEST_FAILED("Credential request failed"),

    /** The holder successfully fetched issuer metadata from an issuer service. */
    ISSUER_METADATA_FETCHED("Issuer metadata fetched"),

    /** The holder received a credential offer from an issuer. */
    CREDENTIAL_OFFER_RECEIVED("Credential offer received"),

    /** A credential delivered by the issuer was successfully stored by the holder. */
    CREDENTIAL_SAVED("Credential saved"),

    /**
     * A batch of issued credentials was fully processed; summarises saved and skipped counts.
     * Fired once at the end of {@code processIssuedCredentials} regardless of partial failures.
     */
    CREDENTIALS_PROCESSED("Credentials processed"),

    /**
     * A credential delivered by the issuer was skipped because the issuer DID
     * is not in the holder's trusted-issuer list.
     */
    CREDENTIAL_UNTRUSTED_ISSUER("Credential from untrusted issuer skipped"),

    /** The issuer sent a rejection notification; the holder recorded the rejected status. */
    CREDENTIAL_REJECTED_BY_ISSUER("Credential rejected by issuer"),

    /**
     * A credential delivery message was received from an issuer and the issuer's
     * identity was successfully verified. Fired in the holder's service layer on
     * successful {@code authorizeIssuer()} before processing begins.
     */
    CREDENTIAL_MESSAGE_RECEIVED("Credential message received"),

    // -------------------------------------------------------------------------
    // Presentation exchange — holder-side
    // -------------------------------------------------------------------------

    /** The holder's Credential Service received a presentation query from a verifier. */
    PRESENTATION_QUERY_RECEIVED("Presentation query received"),

    /** The holder created and returned a Verifiable Presentation in response to a query. */
    PRESENTATION_CREATED("Presentation created"),

    // -------------------------------------------------------------------------
    // Presentation exchange — verifier-side
    // -------------------------------------------------------------------------

    /** The verifier dispatched a presentation query to the holder's Credential Service (Step 4). */
    PRESENTATION_QUERY_SENT("Presentation query sent"),

    /** The verifier successfully validated all presentation JWTs and embedded credentials (Step 5). */
    PRESENTATION_VERIFIED("Presentation verified"),

    /** Presentation validation failed — signature mismatch, expired credential, untrusted issuer, etc. */
    PRESENTATION_INVALID("Presentation validation failed"),

    // -------------------------------------------------------------------------
    // Token / Identity
    // -------------------------------------------------------------------------

    /** The verifier validated a self-issued ID token from the holder (Step 3a). */
    SELF_ISSUED_TOKEN_VALIDATED("Self-issued ID token validated"),

    /**
     * A bearer / self-issued token failed validation anywhere in the flow
     * (issuer, holder, or verifier).
     */
    TOKEN_VALIDATION_FAILED("Token validation failed"),

    /** An access token or self-issued ID token was created and signed. */
    TOKEN_ISSUED("Self-issued ID token issued"),

    /** Identity verification of a DID succeeded. */
    IDENTITY_VERIFIED("Identity verification succeeded"),

    /** Identity verification of a DID failed. */
    IDENTITY_VERIFICATION_FAILED("Identity verification failed"),

    // -------------------------------------------------------------------------
    // Key management
    // -------------------------------------------------------------------------

    /** A signing key was rotated and the new key metadata was persisted. */
    KEY_ROTATED("Signing key rotated");

    // -------------------------------------------------------------------------
    // Infrastructure
    // -------------------------------------------------------------------------

    private final String label;

    private static final Map<String, DcpAuditEventType> BY_LABEL;

    static {
        Map<String, DcpAuditEventType> map = new ConcurrentHashMap<>();
        for (DcpAuditEventType instance : DcpAuditEventType.values()) {
            map.put(instance.label, instance);
            map.put(instance.name(), instance);
        }
        BY_LABEL = Collections.unmodifiableMap(map);
    }

    DcpAuditEventType(String label) {
        this.label = label;
    }

    /**
     * Look up a {@code DcpAuditEventType} by its human-readable label or enum
     * constant name.
     *
     * @param label the label or name to look up (case-sensitive)
     * @return the matching {@code DcpAuditEventType}, or {@code null} if not found
     */
    public static DcpAuditEventType fromLabel(String label) {
        if (label == null) {
            return null;
        }
        return BY_LABEL.get(label);
    }

    /**
     * Returns the human-readable label, which is also used as the JSON serialized value.
     *
     * @return the label string
     */
    @Override
    @JsonValue
    public String toString() {
        return label;
    }
}

