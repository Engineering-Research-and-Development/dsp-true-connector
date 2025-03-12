package it.eng.tools.client.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@ExtendWith(MockitoExtension.class)
public class OkHttpRestClientTest {
	
	private static final String BASIC_AUTH = "basicAuth";
	private static final String TARGET_ADDRESS = "http://test.endpoint";
	@Mock
	private OkHttpClient okHttpClient;
	@Mock
	private CredentialUtils credentialUtils;
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
	
	private OkHttpRestClient okHttpRestClient;
	
	@BeforeEach
	public void setup() {
		okHttpRestClient = new OkHttpRestClient(okHttpClient, credentialUtils, "123", false);
	}
	
	@Test
	@DisplayName("Send protocol request - success")
	public void callSuccessful() throws IOException {
		when(okHttpClient.newCall(request)).thenReturn(call);
		when(call.execute()).thenReturn(response);
		okHttpRestClient.executeCall(request);
	}
	
	@Test
	@DisplayName("Send protocol request - error")
	public void callError() throws IOException {
		when(okHttpClient.newCall(request)).thenReturn(call);
		when(call.execute()).thenThrow(new IOException("Error"));
		
	    assertNull(okHttpRestClient.executeCall(request));
	}
	
	@Test
	@DisplayName("Send protocol request - success")
	public void sendProtocolRequest_success() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		String newString = "{\"test\": \"example\"}";
	    JsonNode jsonNode = mapper.readTree(newString);
	    
	    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);
		
		when(response.code()).thenReturn(200);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn("This is answer from test");
		when(response.isSuccessful()).thenReturn(true);
		
		GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, jsonNode, BASIC_AUTH);
		
		assertNotNull(apiResponse);
		assertTrue(apiResponse.isSuccess());
	}
	
	@Test
	@DisplayName("Send protocol request - error")
	public void sendProtocolRequest_error() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		String newString = "{\"test\": \"example_error\"}";
	    JsonNode jsonNode = mapper.readTree(newString);
	    
	    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);
		
		when(response.code()).thenReturn(400);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn("This is ERROR answer from test");
		when(response.isSuccessful()).thenReturn(false);
		
		GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, jsonNode, BASIC_AUTH);
		
		assertNotNull(apiResponse);
		assertFalse(apiResponse.isSuccess());
	}
	
	@Test
	@DisplayName("Send GET request - success")
	public void sendGETRequest_success() throws IOException {
	    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);
		
		when(response.code()).thenReturn(200);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn("This is answer from test");
		when(response.isSuccessful()).thenReturn(true);
		
		GenericApiResponse<String> apiResponse = okHttpRestClient.sendGETRequest(TARGET_ADDRESS,BASIC_AUTH);
		
		assertNotNull(apiResponse);
		assertTrue(apiResponse.isSuccess());
	}
	
	@Test
	@DisplayName("Send GET request - error")
	public void sendGETRequest_error() throws IOException {
	    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);
		
		when(response.code()).thenReturn(400);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn("This is ERROR answer from test");
		when(response.isSuccessful()).thenReturn(false);
		
		GenericApiResponse<String> apiResponse = okHttpRestClient.sendGETRequest(TARGET_ADDRESS, BASIC_AUTH);
		
		assertNotNull(apiResponse);
		assertFalse(apiResponse.isSuccess());
	}
}
