package it.eng.connector.configuration;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantContextProvider;
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
 * <p>The authenticated principal is the validated Self-Issued ID Token {@code sub} DID (DCP §4.3.3),
 * never a DID taken from the unverified access token carried in its {@code token} claim. A token
 * whose access token names another DID is rejected by {@link VerifierService}.
 *
 * <p>Can be enabled/disabled via the dcp.vp.enabled property.
 * When disabled, this provider returns null to allow fallback to other authentication providers.
 */
@Component
@Slf4j
public class DcpVerifierAuthenticationProvider implements AuthenticationProvider {

    private final VerifierService verifierService;
    private final ParticipantContextProvider participantContextProvider;
    private final DcpProperties dcpProperties;

    @Value("${dcp.vp.enabled:false}")
    private boolean vcVpEnabled;

    /**
     * Constructs a new DcpVerifierAuthenticationProvider.
     *
     * @param verifierService The VerifierService for validating credentials
     * @param participantContextProvider Provider of the participant the connector acts as
     * @param dcpProperties DCP properties, naming the configured participant
     */
    public DcpVerifierAuthenticationProvider(VerifierService verifierService,
                                             ParticipantContextProvider participantContextProvider,
                                             DcpProperties dcpProperties) {
        this.verifierService = verifierService;
        this.participantContextProvider = participantContextProvider;
        this.dcpProperties = dcpProperties;
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

            // The connector acts as the single configured participant until tenants map to participants
            ParticipantContext participant = participantContextProvider
                    .findEnabled(dcpProperties.getParticipant().getId())
                    .orElseThrow(() -> new IllegalStateException("No enabled DCP participant is configured"));

            // Call VerifierService.validateAndQueryHolderPresentations
            // This method will throw SecurityException if validation fails
            VerifierService.PresentationFlowResult result =
                verifierService.validateAndQueryHolderPresentations(selfIssuedIdToken, participant);

            // If we get here, validation succeeded
            log.info("✓ DCP VP Authentication SUCCESS");
            log.info("  - Holder DID: {}", result.getHolderDid());
            log.info("  - Scopes: {}", result.getScopes());
            log.info("  - Validated presentations: {}", result.getValidatedPresentations().size());

            // Principal = validated Self-Issued ID Token sub (VerifierService takes the holder DID from it)
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
