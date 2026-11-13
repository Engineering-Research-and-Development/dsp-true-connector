package it.eng.tools.auth.jwt;

import com.auth0.jwt.exceptions.JWTCreationException;
import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Backs {@link ConnectorCredentialProvider} in {@code INTERNAL} authentication mode by minting the
 * connector-to-connector protocol token locally with {@link JwtService#issueProtocolToken()}.
 *
 * <p>No user login takes place and no user store is read: the token carries only the
 * {@value JwtService#CONNECTOR_ROLE} role, with no tenant, user or email. It is signed with
 * {@code application.security.jwt.secret}, which the receiving connector uses to verify it.
 *
 * <p>Tokens and the signing secret are never logged.
 */
@Slf4j
@Component
@Conditional(InternalAuthenticationModeCondition.class)
public class InternalProtocolTokenIssuer implements ConnectorCredentialProvider {

    private static final String SUBJECT_DETAIL = "subject";

    private final JwtService jwtService;
    private final AuditEventPublisher auditEventPublisher;

    /**
     * Creates the issuer.
     *
     * @param jwtService          the service that signs the protocol token
     * @param auditEventPublisher publisher used to record M2M token issuance audit events
     */
    public InternalProtocolTokenIssuer(JwtService jwtService, AuditEventPublisher auditEventPublisher) {
        this.jwtService = jwtService;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * Mints a new protocol access token.
     *
     * @return the signed token, or {@code null} if signing failed
     */
    @Override
    public String issueConnectorToken() {
        try {
            String token = jwtService.issueProtocolToken();
            auditEventPublisher.publishEvent(
                    AuditEventType.M2M_TOKEN_ISSUED,
                    "Machine-to-machine connector token issued",
                    Map.of(SUBJECT_DETAIL, JwtService.PROTOCOL_TOKEN_SUBJECT));
            return token;
        } catch (JWTCreationException | IllegalArgumentException e) {
            log.error("issueConnectorToken() - Failed to sign the connector protocol token: {}", e.getClass().getSimpleName());
            auditEventPublisher.publishEvent(
                    AuditEventType.M2M_TOKEN_ISSUE_FAILED,
                    "Machine-to-machine connector token issuance failed",
                    Map.of(SUBJECT_DETAIL, JwtService.PROTOCOL_TOKEN_SUBJECT,
                            "error", e.getClass().getSimpleName()));
            return null;
        }
    }
}
