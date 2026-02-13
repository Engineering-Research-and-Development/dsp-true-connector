package it.eng.tools.auth.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Tests for KeycloakAuthenticationService.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakAuthenticationServiceTest {

	@Mock
	private OkHttpClient okHttpClient;

	@Mock
	private Call call;

	@Mock
	private KeycloakAuthenticationProperties keycloakProperties;

	private KeycloakAuthenticationService service;

	@BeforeEach
	void setUp() {
		service = new KeycloakAuthenticationService(keycloakProperties, okHttpClient);
	}

	@Test
	void fetchToken_success() throws Exception {
		// Arrange - setup properties only for tests that need them
		when(keycloakProperties.getClientId()).thenReturn("test-client");
		when(keycloakProperties.getClientSecret()).thenReturn("test-secret");
		when(keycloakProperties.getTokenUrl()).thenReturn("http://localhost:8180/realms/test/protocol/openid-connect/token");
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode responseJson = mapper.createObjectNode();
		responseJson.put("access_token", "test-token-value");
		responseJson.put("token_type", "Bearer");
		responseJson.put("expires_in", 300);

		Response response = new Response.Builder()
				.request(new Request.Builder().url("http://test").build())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.body(ResponseBody.create(responseJson.toString(), MediaType.parse("application/json")))
				.build();

		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		// Act
		String token = service.fetchToken();

		// Assert
		assertNotNull(token);
		assertEquals("test-token-value", token);
	}

	@Test
	void fetchToken_httpError() throws Exception {
		// Arrange
		when(keycloakProperties.getClientId()).thenReturn("test-client");
		when(keycloakProperties.getClientSecret()).thenReturn("test-secret");
		when(keycloakProperties.getTokenUrl()).thenReturn("http://localhost:8180/realms/test/protocol/openid-connect/token");

		Response response = new Response.Builder()
				.request(new Request.Builder().url("http://test").build())
				.protocol(Protocol.HTTP_1_1)
				.code(401)
				.message("Unauthorized")
				.body(ResponseBody.create("{\"error\":\"invalid_client\"}", MediaType.parse("application/json")))
				.build();

		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		// Act
		String token = service.fetchToken();

		// Assert
		assertNull(token);
	}

	@Test
	void fetchToken_nullResponse() throws Exception {
		// Arrange
		when(keycloakProperties.getClientId()).thenReturn("test-client");
		when(keycloakProperties.getClientSecret()).thenReturn("test-secret");
		when(keycloakProperties.getTokenUrl()).thenReturn("http://localhost:8180/realms/test/protocol/openid-connect/token");

		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenThrow(new java.io.IOException("Connection refused"));

		// Act
		String token = service.fetchToken();

		// Assert
		assertNull(token);
	}

	@Test
	void fetchToken_noAccessTokenInResponse() throws Exception {
		// Arrange
		when(keycloakProperties.getClientId()).thenReturn("test-client");
		when(keycloakProperties.getClientSecret()).thenReturn("test-secret");
		when(keycloakProperties.getTokenUrl()).thenReturn("http://localhost:8180/realms/test/protocol/openid-connect/token");

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode responseJson = mapper.createObjectNode();
		responseJson.put("error", "invalid_scope");

		Response response = new Response.Builder()
				.request(new Request.Builder().url("http://test").build())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.body(ResponseBody.create(responseJson.toString(), MediaType.parse("application/json")))
				.build();

		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		// Act
		String token = service.fetchToken();

		// Assert
		assertNull(token);
	}

	@Test
	void validateToken_nullToken() {
		// Act
		boolean result = service.validateToken(null);

		// Assert
		assertFalse(result);
	}

	@Test
	void validateToken_validToken() {
		// Act
		boolean result = service.validateToken("some-token");

		// Assert
		assertTrue(result);
	}
}
//
//
//
//
