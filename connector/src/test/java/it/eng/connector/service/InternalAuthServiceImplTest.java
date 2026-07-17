package it.eng.connector.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.service.AuthService.AuthTokens;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.auth.jwt.RefreshTokenRecord;
import it.eng.tools.auth.jwt.RefreshTokenStore;
import it.eng.tools.auth.jwt.TokenPair;

/**
 * Unit tests for {@link InternalAuthServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class InternalAuthServiceImplTest {

	private static final String EMAIL = "user@test.com";
	private static final String PASSWORD = "password";
	private static final String USER_ID = "user-1";
	private static final String TENANT_ID = "tenant-1";

	@Mock
	private AuthenticationManager authenticationManager;
	@Mock
	private UserRepository userRepository;
	@Mock
	private JwtService jwtService;
	@Mock
	private RefreshTokenStore refreshTokenStore;
	@Mock
	private Authentication authentication;

	private InternalAuthServiceImpl authService;
	private User user;

	@BeforeEach
	void setUp() {
		authService = new InternalAuthServiceImpl(authenticationManager, userRepository, jwtService,
				refreshTokenStore);
		user = User.builder()
				.id(USER_ID)
				.email(EMAIL)
				.password("encoded")
				.role(Role.ADMIN)
				.tenantId(TENANT_ID)
				.enabled(true)
				.expired(false)
				.locked(false)
				.build();
	}

	@Test
	@DisplayName("login() with correct credentials returns a token pair matching the authenticated user's claims")
	void loginWithCorrectCredentialsReturnsTokenPair() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(authentication);
		when(authentication.getName()).thenReturn(EMAIL);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		TokenPair tokenPair = new TokenPair("access-token", "unused-refresh-jwt", 900L);
		when(jwtService.issueTokenPair(eq(USER_ID), eq(EMAIL), anyList(), eq(TENANT_ID), anyMap()))
				.thenReturn(tokenPair);
		when(refreshTokenStore.issue(USER_ID)).thenReturn("refresh-id-1");

		AuthTokens tokens = authService.login(EMAIL, PASSWORD);

		assertEquals("access-token", tokens.accessToken());
		assertEquals("refresh-id-1", tokens.refreshToken());
		assertEquals(900L, tokens.expiresInSeconds());
		verify(jwtService).issueTokenPair(USER_ID, EMAIL, List.of(Role.ADMIN.authorityName()), TENANT_ID, java.util.Map.of());
	}

	@Test
	@DisplayName("login() with wrong password throws BadCredentialsException")
	void loginWithWrongPasswordThrowsBadCredentialsException() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new BadCredentialsException("Bad credentials"));

		assertThrows(BadCredentialsException.class, () -> authService.login(EMAIL, "wrong-password"));
		verify(userRepository, never()).findByEmail(anyString());
	}

	@Test
	@DisplayName("login() for a disabled user throws DisabledException")
	void loginForDisabledUserThrowsDisabledException() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new DisabledException("User is disabled"));

		assertThrows(DisabledException.class, () -> authService.login(EMAIL, PASSWORD));
	}

	@Test
	@DisplayName("login() for a locked user throws LockedException")
	void loginForLockedUserThrowsLockedException() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new LockedException("User is locked"));

		assertThrows(LockedException.class, () -> authService.login(EMAIL, PASSWORD));
	}

	@Test
	@DisplayName("login() for an expired account throws AccountExpiredException")
	void loginForExpiredAccountThrowsAccountExpiredException() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new AccountExpiredException("Account expired"));

		assertThrows(AccountExpiredException.class, () -> authService.login(EMAIL, PASSWORD));
	}

	@Test
	@DisplayName("refresh() with a valid refresh token id returns a new access token and rotates the refresh id")
	void refreshWithValidTokenRotatesAndReturnsNewAccessToken() {
		RefreshTokenRecord rotated = new RefreshTokenRecord("new-refresh-id", USER_ID, java.time.Instant.now());
		when(refreshTokenStore.rotate("old-refresh-id")).thenReturn(Optional.of(rotated));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		TokenPair tokenPair = new TokenPair("new-access-token", "unused-refresh-jwt", 900L);
		when(jwtService.issueTokenPair(eq(USER_ID), eq(EMAIL), anyList(), eq(TENANT_ID), anyMap()))
				.thenReturn(tokenPair);

		AuthTokens tokens = authService.refresh("old-refresh-id");

		assertEquals("new-access-token", tokens.accessToken());
		assertEquals("new-refresh-id", tokens.refreshToken());
		assertEquals(900L, tokens.expiresInSeconds());
	}

	@Test
	@DisplayName("refresh() with an unknown/expired/already-rotated refresh token id throws BadCredentialsException")
	void refreshWithInvalidTokenThrowsBadCredentialsException() {
		when(refreshTokenStore.rotate("unknown-id")).thenReturn(Optional.empty());

		assertThrows(BadCredentialsException.class, () -> authService.refresh("unknown-id"));
		verify(userRepository, never()).findById(anyString());
		verify(jwtService, never()).issueTokenPair(anyString(), anyString(), anyList(), anyString(), anyMap());
	}

	@Test
	@DisplayName("logout() revokes the refresh token id")
	void logoutRevokesRefreshTokenId() {
		authService.logout("refresh-id-1");

		verify(refreshTokenStore, times(1)).revoke("refresh-id-1");
	}

	@Test
	@DisplayName("logout() on an already-revoked/unknown id does not throw")
	void logoutOnUnknownIdDoesNotThrow() {
		authService.logout("unknown-id");

		assertDoesNotThrow(() -> authService.logout("unknown-id"));
	}
}
