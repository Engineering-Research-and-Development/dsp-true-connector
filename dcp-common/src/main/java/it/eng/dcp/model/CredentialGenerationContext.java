package it.eng.dcp.model;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal class for credential generation parameters.
 * This is NOT part of the DCP protocol - it's used internally by the issuance service
 * to pass additional parameters (claims, constraints) when generating credentials.
 *
 * This separates protocol-defined messages from internal business logic requirements.
 */
@Getter
@Builder
public class CredentialGenerationContext {

    /**
     * The protocol-defined credential request.
     */
    private CredentialRequest request;

    /**
     * Claims to include in credentials (key-value pairs).
     * Example: {"country_code": "US", "role": "admin"}
     * These claims are used when generating credential subjects.
     */
    @Builder.Default
    private Map<String, Object> requestedClaims = new HashMap<>();

    /**
     * Constraints that must be verified before issuance.
     * These can come from:
     * - Presentation query definitions
     * - Approval workflow logic
     * - Business rules
     */
    @Builder.Default
    private List<ConstraintRule> constraints = new ArrayList<>();

    /**
     * Additional metadata for credential generation.
     * Can include things like:
     * - Credential validity period
     * - Issuer-specific attributes
     * - Audit information
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Create a simple context from a credential request without constraints.
     *
     * @param request The protocol-defined credential request
     * @return A new context with the request and empty claims/constraints
     */
    public static CredentialGenerationContext fromRequest(CredentialRequest request) {
        return CredentialGenerationContext.builder()
                .request(request)
                .build();
    }

    /**
     * Create a context with requested claims.
     *
     * @param request The protocol-defined credential request
     * @param claims The requested claims for credential generation
     * @return A new context with the request and claims
     */
    public static CredentialGenerationContext withClaims(CredentialRequest request, Map<String, Object> claims) {
        return CredentialGenerationContext.builder()
                .request(request)
                .requestedClaims(claims != null ? new HashMap<>(claims) : new HashMap<>())
                .build();
    }

    /**
     * Create a context with constraints.
     *
     * @param request The protocol-defined credential request
     * @param claims The requested claims for credential generation
     * @param constraints The constraints to verify before issuance
     * @return A new context with the request, claims, and constraints
     */
    public static CredentialGenerationContext withConstraints(CredentialRequest request,
                                                               Map<String, Object> claims,
                                                               List<ConstraintRule> constraints) {
        return CredentialGenerationContext.builder()
                .request(request)
                .requestedClaims(claims != null ? new HashMap<>(claims) : new HashMap<>())
                .constraints(constraints != null ? new ArrayList<>(constraints) : new ArrayList<>())
                .build();
    }
}

