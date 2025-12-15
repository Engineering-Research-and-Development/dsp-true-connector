package it.eng.connector.configuration;

import java.util.Collections;
import java.util.List;

import it.eng.dcp.core.DidResolverService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.ValidationReport;
import it.eng.dcp.model.VerifiablePresentation;
import it.eng.dcp.service.PresentationValidationService;
import it.eng.dcp.service.TokenContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication provider for Verifiable Credentials / Verifiable Presentations.
 * Validates the presentation using the DCP module's PresentationValidationService.
 * Can be enabled/disabled via the dcp.vp.enabled property.
 */
@Component
@Slf4j
public class VcVpAuthenticationProvider implements AuthenticationProvider {

    private final PresentationValidationService presentationValidationService;
    private final DidResolverService didResolverService;

    @Value("${dcp.vp.enabled:false}")
    private boolean vcVpEnabled;

    public VcVpAuthenticationProvider(PresentationValidationService presentationValidationService,
                                       DidResolverService didResolverService) {
        this.presentationValidationService = presentationValidationService;
        this.didResolverService = didResolverService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // Check if VC/VP authentication is enabled
        if (!vcVpEnabled) {
            log.debug("VC/VP authentication is disabled (dcp.vp.enabled=false), skipping to next provider");
            return null; // Return null to allow fallback to next authentication provider
        }

        log.debug("VC/VP authentication - validating presentation");
        VcVpAuthenticationToken vcVpToken = (VcVpAuthenticationToken) authentication;

        PresentationResponseMessage presentation = vcVpToken.getPrincipal();
        if (presentation == null) {
            log.debug("Presentation is null, allowing fallback to next authentication provider");
            return null; // Allow fallback instead of throwing exception
        }

        // For now, we don't require specific credential types - validate any valid presentation
        // You can configure required types based on your requirements
        List<String> requiredCredentialTypes = Collections.emptyList();

        // Create a minimal TokenContext (can be enhanced if you need to pass additional context)
        TokenContext tokenContext = null; // Can be populated from request context if needed

        try {
            // STEP 0: Verify outer JWT (VP) signature if token is JWT format
            String rawToken = vcVpToken.getRawToken();
            if (rawToken != null && !verifyOuterJwtSignature(rawToken)) {
                log.warn("Outer VP JWT signature verification failed, allowing fallback");
                return null;
            }

            // STEP 1: Verify inner VC JWT signatures
            if (!verifyInnerCredentialSignatures(presentation)) {
                log.warn("Inner credential signature verification failed, allowing fallback");
                return null;
            }

            // STEP 2: PLACEHOLDER - Custom claims validation
            // TODO: Add your custom claims validation logic here
            // Example: Check if MembershipCredential has specific values
            if (!validateCustomClaims(presentation)) {
                log.warn("Custom claims validation failed, allowing fallback");
                return null;
            }

            // STEP 3: Validate the presentation using DCP validation service
            ValidationReport report = presentationValidationService.validate(
                presentation,
                requiredCredentialTypes,
                tokenContext
            );

            if (!report.isValid()) {
                log.warn("Presentation validation failed: {}, allowing fallback to next authentication provider", report.getErrors());
                return null; // Allow fallback instead of throwing exception
            }

            // Extract subject from presentation (holder DID or identifier)
            String subject = extractSubject(presentation);

            log.info("VC/VP authentication successful for subject: {}", subject);
            return new VcVpAuthenticationToken(presentation, true, subject);
        } catch (Exception e) {
            log.warn("VC/VP authentication failed with exception: {}, allowing fallback to next authentication provider", e.getMessage());
            return null; // Allow fallback to next provider on any exception
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return VcVpAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * Extract the subject (holder DID) from the presentation.
     * This can be customized based on your credential structure.
     * @param presentation The presentation to extract from
     * @return The subject identifier
     */
    private String extractSubject(PresentationResponseMessage presentation) {
        try {
            List<Object> presentations = presentation.getPresentation();
            if (presentations != null && !presentations.isEmpty()) {
                Object first = presentations.get(0);
                if (first instanceof VerifiablePresentation vp) {
                    // Try to get holder DID from VP
                    if (vp.getHolderDid() != null) {
                        return vp.getHolderDid();
                    }
                    // Or extract from first credential
                    List<String> credIds = vp.getCredentialIds();
                    if (credIds != null && !credIds.isEmpty()) {
                        return "holder-" + credIds.get(0); // Fallback
                    }
                }
            }
            return "unknown-holder";
        } catch (Exception e) {
            log.warn("Failed to extract subject from presentation: {}", e.getMessage());
            return "unknown-holder";
        }
    }

    /**
     * SECURITY: Verify outer JWT (VP) signature.
     * The VP JWT is signed by the holder (the entity presenting the credentials).
     *
     * @param rawToken The raw JWT token string
     * @return true if signature is valid, false otherwise
     */
    private boolean verifyOuterJwtSignature(String rawToken) {
        try {
            // Parse as JWT
            com.nimbusds.jwt.JWT jwt = com.nimbusds.jwt.JWTParser.parse(rawToken);

            // Must be a signed JWT
            if (!(jwt instanceof com.nimbusds.jwt.SignedJWT signedJWT)) {
                log.warn("Outer JWT is not signed");
                return false;
            }

            // Get holder DID from issuer claim
            String holderDid = jwt.getJWTClaimsSet().getIssuer();
            if (holderDid == null || holderDid.isBlank()) {
                log.warn("Outer JWT missing issuer claim");
                return false;
            }

            // Get key ID from header
            String kid = signedJWT.getHeader().getKeyID();
            if (kid == null) {
                log.warn("Outer JWT missing kid in header");
                return false;
            }

            // Resolve the holder's public key using DID resolver
            com.nimbusds.jose.jwk.JWK jwk = didResolverService.resolvePublicKey(holderDid, kid, null);
            if (jwk == null) {
                log.warn("No public key found for holder DID: {}, kid: {}", holderDid, kid);
                return false;
            }

            // Expect EC public key
            com.nimbusds.jose.jwk.ECKey ecKey = (com.nimbusds.jose.jwk.ECKey) jwk;
            com.nimbusds.jose.JWSVerifier verifier = new com.nimbusds.jose.crypto.ECDSAVerifier(ecKey.toECPublicKey());

            // Verify signature
            boolean verified = signedJWT.verify(verifier);

            if (!verified) {
                log.warn("Outer VP JWT signature verification failed for holder: {}", holderDid);
            } else {
                log.debug("Outer VP JWT signature verified for holder: {}", holderDid);
            }

            return verified;

        } catch (Exception e) {
            log.error("Error verifying outer VP JWT signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * SECURITY: Verify inner VC JWT signatures.
     * Each verifiableCredential in the VP can be:
     * 1. JWT format: {"type": "...", "format": "jwt", "jwt": "eyJ..."}
     * 2. JSON format: {"type": "...", "format": "json", "credentialSubject": {...}} (no signature to verify)
     *
     * @param presentation The presentation containing credentials
     * @return true if all credential signatures are valid, false otherwise
     */
    private boolean verifyInnerCredentialSignatures(PresentationResponseMessage presentation) {
        try {
            List<Object> credentialsList = presentation.getPresentation();
            if (credentialsList == null || credentialsList.isEmpty()) {
                log.warn("No credentials found in PresentationResponseMessage");
                return false;
            }

            // Each object in the list is a credential (not a wrapper containing credentials)
            // Credential structure: {"type": "...", "format": "jwt|json", "jwt": "..." | ...credential fields...}
            for (Object credObj : credentialsList) {
                if (credObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> credMap = (java.util.Map<String, Object>) credObj;

                    // Check the format field
                    Object formatObj = credMap.get("format");
                    String format = formatObj != null ? formatObj.toString() : "unknown";

                    if ("jwt".equalsIgnoreCase(format)) {
                        // JWT format - verify signature
                        Object jwtObj = credMap.get("jwt");
                        if (jwtObj instanceof String jwtString) {
                            if (!verifyCredentialJwt(jwtString)) {
                                log.warn("Inner credential JWT signature verification failed");
                                return false;
                            }
                        } else {
                            log.warn("Credential has format='jwt' but no 'jwt' field or is not a string");
                            return false;
                        }
                    } else if ("json".equalsIgnoreCase(format)) {
                        // JSON format - no signature to verify (already decoded)
                        // The outer VP JWT signature covers this credential
                        log.debug("Credential is in JSON format (no separate signature to verify)");
                        // Continue - this is valid
                    } else {
                        log.warn("Unknown credential format: {}", format);
                        return false;
                    }
                } else {
                    log.warn("Credential is not a Map: {}", credObj.getClass().getName());
                    return false;
                }
            }

            log.debug("All inner credential signatures verified successfully");
            return true;

        } catch (Exception e) {
            log.error("Error verifying inner credential signatures: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verify a single credential JWT signature using the issuer's DID.
     *
     * @param jwtString The JWT string representing the credential
     * @return true if signature is valid, false otherwise
     */
    private boolean verifyCredentialJwt(String jwtString) {
        try {
            com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(jwtString);

            // Get issuer from JWT claims
            String issuerDid = signedJWT.getJWTClaimsSet().getIssuer();
            if (issuerDid == null || issuerDid.isBlank()) {
                log.warn("Credential JWT missing issuer claim");
                return false;
            }

            // Get key ID from header
            String kid = signedJWT.getHeader().getKeyID();
            if (kid == null) {
                log.warn("Credential JWT missing kid in header");
                return false;
            }

            // Resolve the issuer's public key using DID resolver
            com.nimbusds.jose.jwk.JWK jwk = didResolverService.resolvePublicKey(issuerDid, kid, null);
            if (jwk == null) {
                log.warn("No public key found for issuer DID: {}, kid: {}", issuerDid, kid);
                return false;
            }

            // Expect EC public key
            com.nimbusds.jose.jwk.ECKey ecKey = (com.nimbusds.jose.jwk.ECKey) jwk;
            com.nimbusds.jose.JWSVerifier verifier = new com.nimbusds.jose.crypto.ECDSAVerifier(ecKey.toECPublicKey());

            // Verify signature
            boolean verified = signedJWT.verify(verifier);

            if (!verified) {
                log.warn("Credential JWT signature verification failed for issuer: {}", issuerDid);
            } else {
                log.debug("Credential JWT signature verified for issuer: {}", issuerDid);
            }

            return verified;

        } catch (Exception e) {
            log.error("Error verifying credential JWT signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * PLACEHOLDER: Custom claims validation.
     *
     * This is where you can add your custom business logic to validate specific claims
     * in the verifiable credentials.
     *
     * Example use cases:
     * - Check if MembershipCredential.membershipType is "Premium"
     * - Verify that credentialSubject.status is "Active"
     * - Validate that membershipId matches a pattern
     * - Check expiration dates beyond standard JWT exp validation
     * - Enforce business rules specific to your domain
     *
     * @param presentation The presentation to validate
     * @return true if custom claims are valid, false otherwise
     */
    private boolean validateCustomClaims(PresentationResponseMessage presentation) {
        try {
            List<Object> credentialsList = presentation.getPresentation();
            if (credentialsList == null || credentialsList.isEmpty()) {
                return true; // No custom validation needed
            }

            // Each object in the list is a credential
            for (Object credObj : credentialsList) {
                if (credObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> credMap = (java.util.Map<String, Object>) credObj;

                    // Check the format field
                    Object formatObj = credMap.get("format");
                    String format = formatObj != null ? formatObj.toString() : "unknown";

                    if ("jwt".equalsIgnoreCase(format)) {
                        // JWT format - parse and validate claims from JWT
                        Object jwtObj = credMap.get("jwt");
                        if (jwtObj instanceof String jwtString) {
                            if (!validateCredentialClaims(jwtString, credMap)) {
                                return false;
                            }
                        }
                    } else if ("json".equalsIgnoreCase(format)) {
                        // JSON format - validate claims directly from the credential map
                        if (!validateJsonCredentialClaims(credMap)) {
                            return false;
                        }
                    }
                }
            }

            return true;

        } catch (Exception e) {
            log.error("Error during custom claims validation: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * PLACEHOLDER: Validate claims in a single credential JWT.
     *
     * TODO: Implement your custom validation logic here.
     *
     * Example implementation:
     * <pre>
     * private boolean validateCredentialClaims(String jwtString, Map<String, Object> credMap) {
     *     try {
     *         SignedJWT signedJWT = SignedJWT.parse(jwtString);
     *         JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
     *
     *         // Get the vc claim
     *         Map<String, Object> vc = (Map<String, Object>) claims.getClaim("vc");
     *         if (vc == null) return false;
     *
     *         // Get credentialSubject
     *         Map<String, Object> credSubject = (Map<String, Object>) vc.get("credentialSubject");
     *         if (credSubject == null) return false;
     *
     *         // Example: Check membershipType
     *         String membershipType = (String) credSubject.get("membershipType");
     *         if (!"Premium".equals(membershipType)) {
     *             log.warn("Invalid membershipType: {}", membershipType);
     *             return false;
     *         }
     *
     *         // Example: Check status
     *         String status = (String) credSubject.get("status");
     *         if (!"Active".equals(status)) {
     *             log.warn("Invalid status: {}", status);
     *             return false;
     *         }
     *
     *         return true;
     *     } catch (Exception e) {
     *         log.error("Error validating credential claims: {}", e.getMessage());
     *         return false;
     *     }
     * }
     * </pre>
     *
     * @param jwtString The JWT string containing the credential
     * @param credMap The credential map (for accessing other fields like type)
     * @return true if claims are valid, false otherwise
     */
    private boolean validateCredentialClaims(String jwtString, java.util.Map<String, Object> credMap) {
        // TODO: Implement your custom validation logic here
        // For now, accept all credentials (signature was already verified)
        log.debug("Custom claims validation placeholder (JWT format) - accepting credential");
        return true;
    }

    /**
     * PLACEHOLDER: Validate claims in a JSON format credential.
     *
     * JSON format credentials are already decoded (not wrapped in JWT).
     * The credential map contains all fields directly.
     *
     * TODO: Implement your custom validation logic here.
     *
     * Example implementation:
     * <pre>
     * private boolean validateJsonCredentialClaims(Map<String, Object> credMap) {
     *     try {
     *         // Get credentialSubject directly from the credential
     *         Map<String, Object> credSubject = (Map<String, Object>) credMap.get("credentialSubject");
     *         if (credSubject == null) return false;
     *
     *         // Example: Check membershipType
     *         String membershipType = (String) credSubject.get("membershipType");
     *         if (!"Premium".equals(membershipType)) {
     *             log.warn("Invalid membershipType: {}", membershipType);
     *             return false;
     *         }
     *
     *         // Example: Check status
     *         String status = (String) credSubject.get("status");
     *         if (!"Active".equals(status)) {
     *             log.warn("Invalid status: {}", status);
     *             return false;
     *         }
     *
     *         return true;
     *     } catch (Exception e) {
     *         log.error("Error validating JSON credential claims: {}", e.getMessage());
     *         return false;
     *     }
     * }
     * </pre>
     *
     * @param credMap The credential map containing all credential fields
     * @return true if claims are valid, false otherwise
     */
    private boolean validateJsonCredentialClaims(java.util.Map<String, Object> credMap) {
        // TODO: Implement your custom validation logic here
        // For now, accept all credentials
        log.debug("Custom claims validation placeholder (JSON format) - accepting credential");
        return true;
    }
}

