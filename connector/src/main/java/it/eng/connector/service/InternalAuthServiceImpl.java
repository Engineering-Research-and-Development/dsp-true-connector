package it.eng.connector.service;

import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.auth.jwt.RefreshTokenRecord;
import it.eng.tools.auth.jwt.RefreshTokenStore;
import it.eng.tools.auth.jwt.TokenPair;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code INTERNAL}-mode {@link AuthService} implementation, authenticating against MongoDB-backed
 * {@code User} credentials.
 *
 * <p>Credential verification is fully delegated to the existing {@link AuthenticationManager} /
 * {@code DaoAuthenticationProvider} / {@code UserDetailsService} wiring registered in
 * {@code ConnectorSecurityConfig} for {@code INTERNAL} mode, so the {@code enabled} / {@code expired}
 * / {@code locked} semantics already enforced there are preserved without duplication here.
 *
 * <p>Unlike {@code UserService}, this service is active strictly in {@code INTERNAL} mode: it has
 * nothing to offer in {@code DISABLED} mode.
 *
 * <p>Passwords, tokens, and the JWT signing secret are never logged, including at {@code DEBUG}
 * level.
 */
@Service
@Slf4j
@Conditional(InternalAuthenticationModeCondition.class)
public class InternalAuthServiceImpl implements AuthService {

	private final AuthenticationManager authenticationManager;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final RefreshTokenStore refreshTokenStore;
	private final AuditEventPublisher publisher;

	/**
	 * Creates the service with its required dependencies.
	 *
	 * @param authenticationManager the {@code INTERNAL}-mode authentication manager
	 * @param userRepository        the user repository
	 * @param jwtService            the JWT issuing/verification service
	 * @param refreshTokenStore     the refresh-token id tracker
	 * @param publisher             the audit event publisher
	 */
	public InternalAuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository,
	                               JwtService jwtService, RefreshTokenStore refreshTokenStore, AuditEventPublisher publisher) {
		this.authenticationManager = authenticationManager;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		this.refreshTokenStore = refreshTokenStore;
		this.publisher = publisher;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AuthTokens login(String email, String password) {
		try {
			Authentication authentication =
					authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
			User user = userRepository.findByEmail(authentication.getName())
					.orElseThrow(() -> new BadCredentialsException("Bad credentials"));

			TokenPair tokenPair = jwtService.issueTokenPair(user.getId(), user.getEmail(),
					List.of(user.getRole().authorityName()), user.getTenantId(), Map.of());
			String refreshTokenId = refreshTokenStore.issue(user.getId());

			log.info("User '{}' logged in successfully", user.getEmail());
			publisher.publishEvent(AuditEvent.Builder.newInstance()
					.eventType(AuditEventType.APPLICATION_LOGIN)
					.description("User logged in successfully")
					.username(user.getEmail())
					.tenantId(user.getTenantId())
					.build());

			return new AuthTokens(tokenPair.accessToken(), refreshTokenId, tokenPair.accessExpiresInSeconds());
		} catch (Exception e) {
			publisher.publishEvent(AuditEvent.Builder.newInstance()
					.eventType(AuditEventType.APPLICATION_LOGIN_FAILED)
					.description("User login failed")
					.username(email)
					.details(auditMap("errorMessage", e.getMessage()))
					.build());
			throw e;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AuthTokens refresh(String refreshTokenId) {
		try {
			RefreshTokenRecord rotated = refreshTokenStore.rotate(refreshTokenId)
					.orElseThrow(() -> new BadCredentialsException("Bad credentials"));
			User user = userRepository.findById(rotated.subject())
					.orElseThrow(() -> new BadCredentialsException("Bad credentials"));

			TokenPair tokenPair = jwtService.issueTokenPair(user.getId(), user.getEmail(),
					List.of(user.getRole().authorityName()), user.getTenantId(), Map.of());

			log.info("Refresh token rotated successfully for user '{}'", user.getEmail());
			publisher.publishEvent(AuditEvent.Builder.newInstance()
					.eventType(AuditEventType.APPLICATION_TOKEN_REFRESHED)
					.description("Refresh token rotated successfully")
					.username(user.getEmail())
					.tenantId(user.getTenantId())
					.build());

			return new AuthTokens(tokenPair.accessToken(), rotated.tokenId(), tokenPair.accessExpiresInSeconds());
		} catch (Exception e) {
			publisher.publishEvent(AuditEvent.Builder.newInstance()
					.eventType(AuditEventType.APPLICATION_TOKEN_REFRESH_FAILED)
					.description("Refresh token rotation failed")
					.details(auditMap("errorMessage", e.getMessage(),
							"refreshTokenId", refreshTokenId))
					.build());
			throw e;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void logout(String refreshTokenId) {
		try {
			Optional<RefreshTokenRecord> rotated = refreshTokenStore.rotate(refreshTokenId);
			Optional<User> user = Optional.empty();
			if (rotated.isPresent()) {
				user = userRepository.findById(rotated.get().subject());
			}
			refreshTokenStore.revoke(refreshTokenId);
			log.info("Refresh token revoked on logout");
			publisher.publishEvent(AuditEvent.Builder.newInstance()
					.eventType(AuditEventType.APPLICATION_LOGOUT)
					.description("Logout successful")
					.username(user.isPresent() ? user.get().getEmail() : null)
					.tenantId(user.isPresent() ? user.get().getTenantId() : null)
					.build());
		} catch (Exception e) {
			publisher.publishEvent(AuditEvent.Builder.newInstance()
					.eventType(AuditEventType.APPLICATION_LOGOUT_FAILED)
					.description("Logout failed")
					.details(auditMap("errorMessage", e.getMessage(),
							"refreshTokenId", refreshTokenId))
					.build());

			throw e;
		}
	}

	/**
	 * Helper to construct audit event maps, silently skipping null values.
	 *
	 * @param keyValuePairs an array of key-value pairs
	 * @return a map containing the non-null key-value pairs
	 */
	private Map<String, Object> auditMap(Object... keyValuePairs) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
			Object value = keyValuePairs[i + 1];
			if (value != null) {
				map.put((String) keyValuePairs[i], value);
			}
		}
		return map;
	}
}