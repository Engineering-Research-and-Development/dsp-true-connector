package it.eng.tools.auth.internal;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.auth.jwt.TokenPair;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Mints JWT access tokens for internal-service (super-admin, tenant-agnostic) machine-to-machine
 * API calls made in {@code INTERNAL} authentication mode, backing {@code CredentialUtils
 * #getAPICredentials()}.
 *
 * <p>Replaces the former {@code InternalAuthenticationService}, which incorrectly passed the raw
 * {@code aapplication.security.jwt.secret} value into {@link JwtService}'s {@code email} parameter —
 * since JWT payloads are base64url-encoded, not encrypted, this leaked the shared secret in
 * cleartext inside the {@code email} claim of every minted internal-service token. This class
 * never embeds the secret in any claim: the secret is used only as a local, non-Mongo minting gate
 * (mirroring the guard already applied by the legacy, now-unwired {@code
 * InternalServiceAuthenticationProvider} Basic Auth bridge), and the token's {@code sub}/{@code
 * email} claim is the fixed {@value #INTERNAL_SERVICE_USERNAME} identifier.
 *
 * <p>The minted token's principal has {@code tenantId=null}, allowing {@code
 * ApiTenantContextFilter} to honour the {@code X-Tenant-Id} request header for correct
 * multi-tenant routing, matching the claim shape granted by {@code
 * InternalServiceAuthenticationProvider}.
 */
@Slf4j
@Component
@Conditional(InternalAuthenticationModeCondition.class)
public class InternalServiceTokenIssuer {

	/** Subject/email claim value used for internal-service machine-to-machine tokens. */
	static final String INTERNAL_SERVICE_USERNAME = "internal-service";

	/** Role claim granted to internal-service tokens, mirroring {@code Role.ADMIN}'s authority name. */
	static final String INTERNAL_SERVICE_ROLE = "ROLE_ADMIN";

	private final JwtService jwtService;
	private final String internalSecret;

	/**
	 * Constructs the issuer.
	 *
	 * @param jwtService     the shared JWT signing/verification service
	 * @param internalSecret the shared secret read from {@code application.security.jwt.secret};
	 *                       defaults to an empty string when the property is absent, which fails
	 *                       {@link #init()}'s startup check
	 */
	public InternalServiceTokenIssuer(JwtService jwtService,
			@Value("${application.security.jwt.secret:}") String internalSecret) {
		this.jwtService = jwtService;
		this.internalSecret = internalSecret;
	}

	/**
	 * Validates that the internal-service secret is configured at application startup, matching
	 * the minting gate previously enforced by {@code InternalServiceAuthenticationProvider}.
	 *
	 * @throws IllegalStateException if {@code application.security.jwt.secret} is blank
	 */
	@PostConstruct
	public void init() {
		if (internalSecret == null || internalSecret.isBlank()) {
			throw new IllegalStateException(
					"Property 'application.security.jwt.secret' must be configured with a non-blank value "
							+ "when 'application.auth.provider=INTERNAL' is active.");
		}
	}

	/**
	 * Mints a fresh access token for the {@value #INTERNAL_SERVICE_USERNAME} principal.
	 *
	 * @return the issued access token
	 */
	public String issueInternalServiceToken() {
		TokenPair tokenPair = jwtService.issueTokenPair(INTERNAL_SERVICE_USERNAME, INTERNAL_SERVICE_USERNAME,
				List.of(INTERNAL_SERVICE_ROLE), null, null);
		return tokenPair.accessToken();
	}
}
