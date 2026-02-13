package it.eng.tools.auth.daps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Tests for DapsAuthenticationService.
 */
@ExtendWith(MockitoExtension.class)
public class DapsAuthenticationServiceTest {

	private DapsAuthenticationService service;

	@Mock
	private DapsAuthenticationProperties dapsProperties;
	@Mock
	private DapsCertificateProvider dapsCertificateProvider;
	@Mock
	private OkHttpClient okHttpClient;
	@Mock
	private Call call;

	@Mock
	private Response response;
	@Mock
	private ResponseBody responseBody;
	@Mock
	private Algorithm algorithm;

	private static final String ACCESS_TOKEN_VALUE = "access token value";

	@Test
	public void fetchTokenSuccess() throws Exception {
		// Arrange
		service = new DapsAuthenticationService(dapsProperties, dapsCertificateProvider, okHttpClient);
		when(dapsCertificateProvider.getDapsV2Jws()).thenReturn("JWS");
		when(dapsProperties.getDapsUrl()).thenReturn(new URL("http://daps.url"));
		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		when(response.isSuccessful()).thenReturn(true);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn(createAccessTokenResponse());

		// Act
		String token = service.fetchToken();

		// Assert
		assertEquals(ACCESS_TOKEN_VALUE, token);
	}

	@Test
	public void fetchTokenResponseBodyNull() throws Exception {
		// Arrange
		service = new DapsAuthenticationService(dapsProperties, dapsCertificateProvider, okHttpClient);
		when(dapsCertificateProvider.getDapsV2Jws()).thenReturn("JWS");
		when(dapsProperties.getDapsUrl()).thenReturn(new URL("http://daps.url"));
		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		when(response.isSuccessful()).thenReturn(true);
		when(response.body()).thenReturn(null);

		// Act
		String token = service.fetchToken();

		// Assert
		assertNull(token);
	}

	@Test
	public void fetchTokenNoAccessTokenInResponse() throws Exception {
		// Arrange
		service = new DapsAuthenticationService(dapsProperties, dapsCertificateProvider, okHttpClient);
		when(dapsCertificateProvider.getDapsV2Jws()).thenReturn("JWS");
		when(dapsProperties.getDapsUrl()).thenReturn(new URL("http://daps.url"));
		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		when(response.isSuccessful()).thenReturn(true);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn("NOT ACCESS TOKEN");

		// Act
		String token = service.fetchToken();

		// Assert
		assertNull(token);
	}

	@Test
	public void fetchTokenHttpError() throws Exception {
		// Arrange
		service = new DapsAuthenticationService(dapsProperties, dapsCertificateProvider, okHttpClient);
		when(dapsCertificateProvider.getDapsV2Jws()).thenReturn("JWS");
		when(dapsProperties.getDapsUrl()).thenReturn(new URL("http://daps.url"));
		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		when(response.isSuccessful()).thenReturn(false);

		// Act
		String token = service.fetchToken();

		// Assert
		assertNull(token);
	}

	@Test
	public void validateTokenSuccess() {
		service = new DapsAuthenticationService(dapsProperties, dapsCertificateProvider, okHttpClient);
		// Arrange
		String token = DapsAuthTestUtils.createTestToken();
		when(dapsProperties.getAlogirthm(any(DecodedJWT.class))).thenReturn(algorithm);
		doNothing().when(algorithm).verify(any(DecodedJWT.class));

		// Act & Assert
		assertTrue(service.validateToken(token));
	}

	@Test
	public void validateTokenNullToken() {
		service = new DapsAuthenticationService(dapsProperties, dapsCertificateProvider, okHttpClient);
		// Act & Assert
		assertFalse(service.validateToken(null));
	}

	@Test
	public void validateTokenSignatureVerificationFails() {
		// Arrange
		service = new DapsAuthenticationService(dapsProperties, dapsCertificateProvider, okHttpClient);
		String token = DapsAuthTestUtils.createTestToken();
		when(dapsProperties.getAlogirthm(any(DecodedJWT.class))).thenReturn(algorithm);
		doThrow(SignatureVerificationException.class).when(algorithm).verify(any(DecodedJWT.class));

		// Act & Assert
		assertFalse(service.validateToken(token));
	}

	/**
	 * Helper method to create a valid access token response JSON.
	 */
	private String createAccessTokenResponse() throws JsonProcessingException {
		Map<String, String> tokenMap = new HashMap<>();
		tokenMap.put("access_token", ACCESS_TOKEN_VALUE);

		return new ObjectMapper().writeValueAsString(tokenMap);
	}
}
