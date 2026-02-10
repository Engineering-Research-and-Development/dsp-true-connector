package it.eng.connector.configuration;

import it.eng.dcp.verifier.service.VerifierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Authentication provider that validates DCP self-issued ID tokens using VerifierService.
 *
 * <p>This provider integrates with the complete DCP presentation flow:
 * <ul>
 *   <li>Step 3a-3c: Validates self-issued ID token, extracts access token, resolves holder DID</li>
 *   <li>Step 4: Queries holder's credential service for presentations</li>
 *   <li>Step 5: Validates presentations and embedded credentials</li>
 * </ul>
 *
 * <p>Can be enabled/disabled via the dcp.vp.enabled property.
 * When disabled, this provider returns null to allow fallback to other authentication providers.
 */
@Component
@Slf4j
public class DcpVerifierAuthenticationProvider implements AuthenticationProvider {

    private final VerifierService verifierService;

    @Value("${dcp.vp.enabled:false}")
    private boolean vcVpEnabled;

    /**
     * Constructs a new DcpVerifierAuthenticationProvider.
     *
     * @param verifierService The VerifierService for validating credentials
     */
    public DcpVerifierAuthenticationProvider(VerifierService verifierService) {
        this.verifierService = verifierService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // Check if DCP VP authentication is enabled
        if (!vcVpEnabled) {
            log.debug("DCP VP authentication is disabled (dcp.vp.enabled=false), skipping to next provider");
            return null; // Return null to allow fallback to next authentication provider
        }

        DcpBearerToken bearerToken = (DcpBearerToken) authentication;
        String selfIssuedIdToken = bearerToken.getToken();

        try {
            log.debug("Attempting DCP VP authentication using VerifierService");

            // Call VerifierService.validateAndQueryHolderPresentations
            // This method will throw SecurityException if validation fails
            VerifierService.PresentationFlowResult result =
                verifierService.validateAndQueryHolderPresentations(selfIssuedIdToken);

            // If we get here, validation succeeded
            log.info("✓ DCP VP Authentication SUCCESS");
            log.info("  - Holder DID: {}", result.getHolderDid());
            log.info("  - Scopes: {}", result.getScopes());
            log.info("  - Validated presentations: {}", result.getValidatedPresentations().size());

            // Create authenticated token with the holder DID as principal
            UsernamePasswordAuthenticationToken authenticatedToken = new UsernamePasswordAuthenticationToken(
                result.getHolderDid(),
                selfIssuedIdToken,
                List.of(new SimpleGrantedAuthority("ROLE_CONNECTOR"))
            );

            log.debug("DCP VP authentication successful for holder: {}", result.getHolderDid());
            return authenticatedToken;

        } catch (SecurityException e) {
            // Validation failed - log and return null to allow fallback
            log.debug("DCP VP authentication failed: {}, allowing fallback to next provider", e.getMessage());
            return null;
        } catch (IOException e) {
            // Network error communicating with holder
            log.warn("DCP VP authentication network error: {}, allowing fallback to next provider", e.getMessage());
            return null;
        } catch (Exception e) {
            // Any other error - log and return null
            log.debug("DCP VP authentication error: {}, allowing fallback to next provider", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return DcpBearerToken.class.isAssignableFrom(authentication);
    }
}
