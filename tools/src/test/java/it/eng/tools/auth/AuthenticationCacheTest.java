package it.eng.tools.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.tools.auth.daps.DapsAuthTestUtils;
import it.eng.tools.auth.daps.DapsAuthenticationProperties;
import it.eng.tools.auth.daps.DapsAuthenticationService;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationProperties;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;

@ExtendWith(MockitoExtension.class)
public class AuthenticationCacheTest {

	private AuthenticationCache authenticationCache;

	@Mock
	private DapsAuthenticationService dapsAuthService;
	@Mock
	private DapsAuthenticationProperties dapsProperties;
	@Mock
	private KeycloakAuthenticationService keycloakAuthService;
	@Mock
	private KeycloakAuthenticationProperties keycloakProperties;

	@BeforeEach
	void setUp() {
		// Create cache with the mocked service in the list
		authenticationCache = new AuthenticationCache(
			List.of(dapsAuthService),
			dapsProperties,
			null
		);
	}

	@Test
	public void cacheDisabled() {
		// When no properties are configured, should return dummy token
		String token = authenticationCache.getToken();

		verify(dapsAuthService, times(0)).fetchToken();
		assertEquals(AuthenticationCache.DUMMY_TOKEN_VALUE, token);
	}

	@Test
	public void cacheEnabled() throws IllegalAccessException {
		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(true);
		when(dapsProperties.isTokenCaching()).thenReturn(true);

		// Set cached token and expiration time
		FieldUtils.writeField(authenticationCache, "cachedToken", "ABC", true);
		FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().plusDays(1L), true);

		String token = authenticationCache.getToken();

		// Should use cached token, not fetch new one
		verify(dapsAuthService, times(0)).fetchToken();
		assertEquals("ABC", token);
	}

	@Test
	public void cacheEnabledTokenExpired() throws IllegalAccessException {
		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(true);
		when(dapsProperties.isTokenCaching()).thenReturn(true);
		when(dapsAuthService.fetchToken()).thenReturn(DapsAuthTestUtils.createTestToken());

		// Set expired token
		FieldUtils.writeField(authenticationCache, "cachedToken", "ABC", true);
		FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().minusDays(1L), true);

		String token = authenticationCache.getToken();

		// Should fetch new token since cached one is expired
		assertNotNull(token);
		verify(dapsAuthService).fetchToken();
	}

	@Test
	public void cacheEnabledTokenInvalid() throws IllegalAccessException {
		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(true);
		when(dapsProperties.isTokenCaching()).thenReturn(true);
		when(dapsAuthService.fetchToken()).thenReturn("INVALID");

		// Set expired token to force refresh
		FieldUtils.writeField(authenticationCache, "cachedToken", "ABC", true);
		FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().minusDays(1L), true);

		String token = authenticationCache.getToken();

		// Should return null because the fetched token is invalid (can't decode JWT)
		assertNull(token);
		verify(dapsAuthService).fetchToken();
	}

	// ========== Keycloak Tests ==========

	@Test
	public void keycloakCacheEnabled() throws IllegalAccessException {
		// Create cache with Keycloak service
		authenticationCache = new AuthenticationCache(
			List.of(keycloakAuthService),
			null,
			keycloakProperties
		);

		when(keycloakProperties.isTokenCaching()).thenReturn(true);

		// Set cached Keycloak token
		FieldUtils.writeField(authenticationCache, "cachedToken", "KEYCLOAK_TOKEN", true);
		FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().plusDays(1L), true);

		String token = authenticationCache.getToken();

		// Should use cached token, not fetch new one
		verify(keycloakAuthService, times(0)).fetchToken();
		assertEquals("KEYCLOAK_TOKEN", token);
	}

	@Test
	public void keycloakCacheDisabled() {
		// Create cache with Keycloak service
		authenticationCache = new AuthenticationCache(
			List.of(keycloakAuthService),
			null,
			keycloakProperties
		);

		when(keycloakProperties.isTokenCaching()).thenReturn(false);
		when(keycloakAuthService.fetchToken()).thenReturn(DapsAuthTestUtils.createTestToken());

		String token = authenticationCache.getToken();

		// Should fetch new token every time
		assertNotNull(token);
		verify(keycloakAuthService).fetchToken();
	}

	@Test
	public void keycloakTokenExpired() throws IllegalAccessException {
		// Create cache with Keycloak service
		authenticationCache = new AuthenticationCache(
			List.of(keycloakAuthService),
			null,
			keycloakProperties
		);

		when(keycloakProperties.isTokenCaching()).thenReturn(true);
		when(keycloakAuthService.fetchToken()).thenReturn(DapsAuthTestUtils.createTestToken());

		// Set expired token
		FieldUtils.writeField(authenticationCache, "cachedToken", "EXPIRED", true);
		FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().minusDays(1L), true);

		String token = authenticationCache.getToken();

		// Should fetch new token since cached one is expired
		assertNotNull(token);
		verify(keycloakAuthService).fetchToken();
	}

	@Test
	public void keycloakTokenInvalid() throws IllegalAccessException {
		// Create cache with Keycloak service
		authenticationCache = new AuthenticationCache(
			List.of(keycloakAuthService),
			null,
			keycloakProperties
		);

		when(keycloakProperties.isTokenCaching()).thenReturn(true);
		when(keycloakAuthService.fetchToken()).thenReturn("INVALID");

		// Set expired token to force refresh
		FieldUtils.writeField(authenticationCache, "cachedToken", "OLD", true);
		FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().minusDays(1L), true);

		String token = authenticationCache.getToken();

		// Should return null because the fetched token is invalid
		assertNull(token);
		verify(keycloakAuthService).fetchToken();
	}

	// ========== Provider Selection Tests ==========

	@Test
	public void providerSelectionKeycloakPreferred() {
		// Create cache with BOTH providers
		authenticationCache = new AuthenticationCache(
			List.of(dapsAuthService, keycloakAuthService),
			dapsProperties,
			keycloakProperties
		);

		when(keycloakProperties.isTokenCaching()).thenReturn(false);
		when(keycloakAuthService.fetchToken()).thenReturn("KEYCLOAK_TOKEN");

		String token = authenticationCache.getToken();

		// Should use Keycloak (priority over DAPS)
		assertEquals("KEYCLOAK_TOKEN", token);
		verify(keycloakAuthService, times(1)).fetchToken();
		verify(dapsAuthService, times(0)).fetchToken();  // Should NOT use DAPS
	}

	@Test
	public void providerSelectionFallbackToDaps() {
		// Create cache with both providers but Keycloak properties null
		authenticationCache = new AuthenticationCache(
			List.of(dapsAuthService, keycloakAuthService),
			dapsProperties,
			null  // No Keycloak properties - should fallback to DAPS
		);

		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(true);
		when(dapsProperties.isTokenCaching()).thenReturn(false);
		when(dapsAuthService.fetchToken()).thenReturn("DAPS_TOKEN");

		String token = authenticationCache.getToken();

		// Should use DAPS when Keycloak not configured
		assertEquals("DAPS_TOKEN", token);
		verify(dapsAuthService, times(1)).fetchToken();
		verify(keycloakAuthService, times(0)).fetchToken();
	}

	@Test
	public void providerSelectionDapsDisabled() {
		// DAPS properties present but DAPS interaction disabled
		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(false);

		String token = authenticationCache.getToken();

		// Should return dummy token when DAPS is disabled
		assertEquals(AuthenticationCache.DUMMY_TOKEN_VALUE, token);
		verify(dapsAuthService, times(0)).fetchToken();
	}

	// ========== Edge Case Tests ==========

	@Test
	public void noProvidersReturnsDummyToken() {
		// Create cache with empty provider list
		authenticationCache = new AuthenticationCache(
			List.of(),
			null,
			null
		);

		String token = authenticationCache.getToken();

		// Should return dummy token when no providers configured
		assertEquals(AuthenticationCache.DUMMY_TOKEN_VALUE, token);
	}

	@Test
	public void nullProvidersReturnsDummyToken() {
		// Create cache with null provider list
		authenticationCache = new AuthenticationCache(
			null,
			null,
			null
		);

		String token = authenticationCache.getToken();

		// Should return dummy token when providers is null
		assertEquals(AuthenticationCache.DUMMY_TOKEN_VALUE, token);
	}

	@Test
	public void noPropertiesConfiguredReturnsDummyToken() {
		// Create cache with providers but no properties
		authenticationCache = new AuthenticationCache(
			List.of(dapsAuthService, keycloakAuthService),
			null,
			null
		);

		String token = authenticationCache.getToken();

		// Should return dummy token when neither DAPS nor Keycloak properties configured
		assertEquals(AuthenticationCache.DUMMY_TOKEN_VALUE, token);
		verify(dapsAuthService, times(0)).fetchToken();
		verify(keycloakAuthService, times(0)).fetchToken();
	}
}





