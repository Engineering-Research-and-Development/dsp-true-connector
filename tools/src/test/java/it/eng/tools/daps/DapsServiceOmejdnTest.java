package it.eng.tools.daps;

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

import it.eng.tools.client.rest.OkHttpRestClient;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@ExtendWith(MockitoExtension.class)
public class DapsServiceOmejdnTest {

	@InjectMocks
	private DapsServiceOmejdn service;

	@Mock
	private DapsProperties dapsProperties;
	@Mock
	private DapsCertificateProviderOmejdn dapsCertificateProvider;
	@Mock
	private OkHttpRestClient client;

	@Mock
	private RequestBody formBody;
	@Mock
	private Request request;
	@Mock
	private Call call;
	@Mock
	private Response response;
	@Mock
	private ResponseBody responseBody;
	@Mock
	private DecodedJWT jwt;
	@Mock
	private Algorithm algorithm;

	private String ACCESS_TOKEN_VALUE = "access token value";

	@Test
	public void fetchTokenSuccess() throws IOException {
		when(dapsCertificateProvider.getDapsV2Jws()).thenReturn("JWS");
		when(dapsProperties.getDapsUrl()).thenReturn(new URL("http://daps.url"));
		when(client.executeCall(any(Request.class))).thenReturn(response);
		
		when(response.isSuccessful()).thenReturn(true);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn(accessToken());

		String token = service.fetchToken();
		
		assertEquals(ACCESS_TOKEN_VALUE, token);
	}

	@Test
	public void fetchTokenSuccessBodyNull() throws MalformedURLException {
		when(dapsCertificateProvider.getDapsV2Jws()).thenReturn("JWS");
		when(dapsProperties.getDapsUrl()).thenReturn(new URL("http://daps.url"));
		when(client.executeCall(any(Request.class))).thenReturn(response);
		
		when(response.isSuccessful()).thenReturn(true);
		when(response.body()).thenReturn(null);

		String token = service.fetchToken();
		
		assertNull(token);
	}
	
	@Test
	public void fetchTokenSuccessNotAccessToken() throws IOException {
		when(dapsCertificateProvider.getDapsV2Jws()).thenReturn("JWS");
		when(dapsProperties.getDapsUrl()).thenReturn(new URL("http://daps.url"));
		when(client.executeCall(any(Request.class))).thenReturn(response);
		
		when(response.isSuccessful()).thenReturn(true);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn("NOT ACCESS TOKEN");

		String token = service.fetchToken();
		
		assertNull(token);
	}
	
	@Test
	public void fetchTokenFailed() throws MalformedURLException {
		when(dapsCertificateProvider.getDapsV2Jws()).thenReturn("JWS");
		when(dapsProperties.getDapsUrl()).thenReturn(new URL("http://daps.url"));
		when(client.executeCall(any(Request.class))).thenReturn(response);
		
		when(response.isSuccessful()).thenReturn(false);

		String token = service.fetchToken();
		
		assertNull(token);
	}
	
	@Test
	public void validateTokenSuccess() {
		String token = DapsUtils.createTestToken();
		when(dapsProperties.getAlogirthm(any(DecodedJWT.class))).thenReturn(algorithm);
		doNothing().when(algorithm).verify(any(DecodedJWT.class));
		
		assertTrue(service.validateToken(token));
	}
	
	@Test
	public void validateTokenFailNull() {
		assertFalse(service.validateToken(null));
	}
	
	@Test
	public void validateTokenFailSignature() {
		String token = DapsUtils.createTestToken();
		when(dapsProperties.getAlogirthm(any(DecodedJWT.class))).thenReturn(algorithm);
		doThrow(SignatureVerificationException.class).when(algorithm).verify(any(DecodedJWT.class));
		
		assertFalse(service.validateToken(token));
	}

	private String accessToken() throws JsonProcessingException {
		Map<String, String> tokenMap = new HashMap<>();
		tokenMap.put("access_token", ACCESS_TOKEN_VALUE );
		
		return new ObjectMapper().writeValueAsString(tokenMap);
	}
}
