package it.eng.connector.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code connector}-module adapter that backs {@link ConnectorCredentialProvider} in {@code
 * INTERNAL} authentication mode by authenticating as the real, seeded Mongo {@code
 * connector@mail.com} CONNECTOR-role user via {@link AuthService}.
 *
 * <p>This is the {@code tools}-module-friendly bridge for {@code CredentialUtils
 * #getConnectorCredentials()}: {@code tools} cannot depend on {@code connector} (which owns the
 * Mongo-backed {@code User}/{@code AuthService} types), so this adapter is registered as a bean
 * here and consumed by {@code tools} through the {@link ConnectorCredentialProvider} interface via
 * an optional {@code ObjectProvider}, mirroring the existing {@code AuthProvider} pattern used for
 * the Keycloak-oriented {@code AuthenticationCache}.
 */
@Slf4j
@Component
@Conditional(InternalAuthenticationModeCondition.class)
public class ConnectorCredentialProviderImpl implements ConnectorCredentialProvider {

	// Seeded connector-to-connector Mongo user credentials; matches the account already hardcoded
	// in CredentialUtils' legacy Basic Auth fallback, so this change alters only *how* the
	// existing seeded account authenticates, not *which* account is used.
	private static final String CONNECTOR_EMAIL = "connector@mail.com";
	private static final String CONNECTOR_PASSWORD = "password";

	private final AuthService authService;
	private final AuditEventPublisher auditEventPublisher;

	/**
	 * Constructs the adapter.
	 *
	 * @param authService         the active {@link AuthService} bean, which resolves to {@code
	 *                            InternalAuthServiceImpl} whenever this adapter's own {@code
	 *                            INTERNAL}-mode condition is satisfied
	 * @param auditEventPublisher publisher used to record M2M token issuance audit events
	 */
	public ConnectorCredentialProviderImpl(AuthService authService, AuditEventPublisher auditEventPublisher) {
		this.authService = authService;
		this.auditEventPublisher = auditEventPublisher;
	}

	@Override
	public String issueConnectorToken() {
		try {
			String token = authService.login(CONNECTOR_EMAIL, CONNECTOR_PASSWORD).accessToken();
			auditEventPublisher.publishEvent(
					AuditEventType.M2M_TOKEN_ISSUED,
					"Machine-to-machine connector token issued",
					Map.of("email", CONNECTOR_EMAIL));
			return token;
		} catch (AuthenticationException e) {
			log.error("issueConnectorToken() - Failed to authenticate seeded connector user '{}': {}",
					CONNECTOR_EMAIL, e.getMessage());
			auditEventPublisher.publishEvent(
					AuditEventType.M2M_TOKEN_ISSUE_FAILED,
					"Machine-to-machine connector token issuance failed",
					Map.of("email", CONNECTOR_EMAIL, "error", e.getMessage()));
			return null;
		}
	}
}
