package it.eng.connector.service;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.auth.jwt.RefreshTokenRecord;
import it.eng.tools.auth.jwt.RefreshTokenStore;
import it.eng.tools.auth.jwt.TokenPair;
import lombok.extern.slf4j.Slf4j;

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

	/**
	 * Creates the service with its required dependencies.
	 *
	 * @param authenticationManager the {@code INTERNAL}-mode authentication manager
	 * @param userRepository        the user repository
	 * @param jwtService            the JWT issuing/verification service
	 * @param refreshTokenStore     the refresh-token id tracker
	 */
	public InternalAuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository,
			JwtService jwtService, RefreshTokenStore refreshTokenStore) {
		this.authenticationManager = authenticationManager;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		this.refreshTokenStore = refreshTokenStore;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AuthTokens login(String email, String password) {
		Authentication authentication =
				authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
		User user = userRepository.findByEmail(authentication.getName())
				.orElseThrow(() -> new BadCredentialsException("Bad credentials"));

		TokenPair tokenPair = jwtService.issueTokenPair(user.getId(), user.getEmail(),
				List.of(user.getRole().authorityName()), user.getTenantId(), Map.of());
		String refreshTokenId = refreshTokenStore.issue(user.getId());

		log.info("User '{}' logged in successfully", user.getEmail());
		return new AuthTokens(tokenPair.accessToken(), refreshTokenId, tokenPair.accessExpiresInSeconds());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AuthTokens refresh(String refreshTokenId) {
		RefreshTokenRecord rotated = refreshTokenStore.rotate(refreshTokenId)
				.orElseThrow(() -> new BadCredentialsException("Bad credentials"));
		User user = userRepository.findById(rotated.subject())
				.orElseThrow(() -> new BadCredentialsException("Bad credentials"));

		TokenPair tokenPair = jwtService.issueTokenPair(user.getId(), user.getEmail(),
				List.of(user.getRole().authorityName()), user.getTenantId(), Map.of());

		log.info("Refresh token rotated successfully for user '{}'", user.getEmail());
		return new AuthTokens(tokenPair.accessToken(), rotated.tokenId(), tokenPair.accessExpiresInSeconds());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void logout(String refreshTokenId) {
		refreshTokenStore.revoke(refreshTokenId);
		log.info("Refresh token revoked on logout");
	}
}
