package it.eng.connector.service;

import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.service.AuthService.AuthTokens;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.auth.jwt.RefreshTokenRecord;
import it.eng.tools.auth.jwt.RefreshTokenStore;
import it.eng.tools.auth.jwt.TokenPair;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exception.TenantNotFoundException;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
	@Mock
	private AuditEventPublisher auditEventPublisher;
	@Mock
	private TenantService tenantService;

	private InternalAuthServiceImpl authService;
	private User user;

	@BeforeEach
	void setUp() {
		authService = new InternalAuthServiceImpl(authenticationManager, userRepository, jwtService,
				refreshTokenStore, tenantService, auditEventPublisher);
		user = User.builder()
				.id(USER_ID)
				.firstName("John")
				.lastName("Doe")
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
		when(refreshTokenStore.issue(EMAIL)).thenReturn("refresh-id-1");
		Tenant tenant = mock(Tenant.class);
		when(tenant.getName()).thenReturn("ACME");
		when(tenantService.findById(TENANT_ID)).thenReturn(tenant);

		AuthTokens tokens = authService.login(EMAIL, PASSWORD);

		assertEquals("access-token", tokens.accessToken());
		assertEquals("refresh-id-1", tokens.refreshToken());
		assertEquals(900L, tokens.expiresInSeconds());
		verify(jwtService).issueTokenPair(USER_ID, EMAIL, List.of(Role.ADMIN.name()), TENANT_ID,
				Map.of(JwtService.GIVEN_NAME_CLAIM, "John", JwtService.FAMILY_NAME_CLAIM, "Doe",
						JwtService.TENANT_NAME_CLAIM, "ACME"));
		verify(auditEventPublisher).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGIN));
	}

	@Test
	@DisplayName("login() with wrong password throws BadCredentialsException")
	void loginWithWrongPasswordThrowsBadCredentialsException() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new BadCredentialsException("Bad credentials"));

		assertThrows(BadCredentialsException.class, () -> authService.login(EMAIL, "wrong-password"));
		verify(userRepository, never()).findByEmail(anyString());
		verify(jwtService, never()).issueTokenPair(anyString(), anyString(), anyList(), anyString(), anyMap());
		verify(auditEventPublisher).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGIN_FAILED));
	}

	@Test
	@DisplayName("login() for a disabled user throws DisabledException")
	void loginForDisabledUserThrowsDisabledException() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new DisabledException("User is disabled"));

		assertThrows(DisabledException.class, () -> authService.login(EMAIL, PASSWORD));
		verify(userRepository, never()).findByEmail(anyString());
		verify(jwtService, never()).issueTokenPair(anyString(), anyString(), anyList(), anyString(), anyMap());
		verify(auditEventPublisher).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGIN_FAILED));
	}

	@Test
	@DisplayName("login() for a locked user throws LockedException")
	void loginForLockedUserThrowsLockedException() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new LockedException("User is locked"));

		assertThrows(LockedException.class, () -> authService.login(EMAIL, PASSWORD));
		verify(userRepository, never()).findByEmail(anyString());
		verify(jwtService, never()).issueTokenPair(anyString(), anyString(), anyList(), anyString(), anyMap());
		verify(auditEventPublisher).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGIN_FAILED));
	}

	@Test
	@DisplayName("login() for an expired account throws AccountExpiredException")
	void loginForExpiredAccountThrowsAccountExpiredException() {
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new AccountExpiredException("Account expired"));

		assertThrows(AccountExpiredException.class, () -> authService.login(EMAIL, PASSWORD));
		verify(userRepository, never()).findByEmail(anyString());
		verify(jwtService, never()).issueTokenPair(anyString(), anyString(), anyList(), anyString(), anyMap());
		verify(auditEventPublisher).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGIN_FAILED));
	}

	@Test
	@DisplayName("refresh() with a valid refresh token id returns a new access token and rotates the refresh id")
	void refreshWithValidTokenRotatesAndReturnsNewAccessToken() {
		RefreshTokenRecord rotated = new RefreshTokenRecord("new-refresh-id", EMAIL, Instant.now());
		when(refreshTokenStore.rotate("old-refresh-id")).thenReturn(Optional.of(rotated));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(tenantService.findById(TENANT_ID)).thenThrow(new TenantNotFoundException("missing"));
		TokenPair tokenPair = new TokenPair("new-access-token", "unused-refresh-jwt", 900L);
		when(jwtService.issueTokenPair(eq(USER_ID), eq(EMAIL), anyList(), eq(TENANT_ID), anyMap()))
				.thenReturn(tokenPair);

		AuthTokens tokens = authService.refresh("old-refresh-id");

		assertEquals("new-access-token", tokens.accessToken());
		assertEquals("new-refresh-id", tokens.refreshToken());
		assertEquals(900L, tokens.expiresInSeconds());
		// A missing tenant leaves out the tenant name claim instead of failing the refresh.
		verify(jwtService).issueTokenPair(USER_ID, EMAIL, List.of(Role.ADMIN.name()), TENANT_ID,
				Map.of(JwtService.GIVEN_NAME_CLAIM, "John", JwtService.FAMILY_NAME_CLAIM, "Doe"));
		verify(auditEventPublisher).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_TOKEN_REFRESHED));
	}

	@Test
	@DisplayName("refresh() with an unknown/expired/already-rotated refresh token id throws BadCredentialsException")
	void refreshWithInvalidTokenThrowsBadCredentialsException() {
		when(refreshTokenStore.rotate("unknown-id")).thenReturn(Optional.empty());

		assertThrows(BadCredentialsException.class, () -> authService.refresh("unknown-id"));
		verify(userRepository, never()).findByEmail(anyString());
		verify(jwtService, never()).issueTokenPair(anyString(), anyString(), anyList(), anyString(), anyMap());
		verify(auditEventPublisher).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_TOKEN_REFRESH_FAILED));
	}

	@Test
	@DisplayName("logout() revokes the refresh token id")
	void logoutRevokesRefreshTokenId() {
		authService.logout("refresh-id-1");

		verify(refreshTokenStore, times(1)).revoke("refresh-id-1");
		verify(auditEventPublisher).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGOUT));
	}

	@Test
	@DisplayName("logout() on an already-revoked/unknown id does not throw")
	void logoutOnUnknownIdDoesNotThrow() {

		assertDoesNotThrow(() -> {
			authService.logout("unknown-id");
			authService.logout("unknown-id");
		});
		verify(refreshTokenStore, times(2)).revoke("unknown-id");
		verify(auditEventPublisher, times(2)).publishEvent(
				argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGOUT));
	}
}