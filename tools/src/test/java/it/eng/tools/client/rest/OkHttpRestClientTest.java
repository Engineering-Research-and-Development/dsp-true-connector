package it.eng.tools.client.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.common.net.HttpHeaders;
import it.eng.tools.model.ExternalData;
import okhttp3.*;
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

@ExtendWith(MockitoExtension.class)
public class OkHttpRestClientTest {
	
	private static final String BASIC_AUTH = "basicAuth";
	private static final String TARGET_ADDRESS = "http://test.endpoint/123";
	private static final String ATTACHMENT_FILENAME = "attachment;filename=";

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

	@Test
	@DisplayName("Download data - success")
	public void downloadData_success() throws IOException {
		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		byte[] bodyBytes = "This is answer from test".getBytes();
		InputStream stream = new ByteArrayInputStream(bodyBytes);
		when(response.code()).thenReturn(200);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.byteStream()).thenReturn(stream);
		when(response.body().contentType()).thenReturn(MediaType.get("text/plain"));
		when(response.isSuccessful()).thenReturn(true);

		GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

		assertNotNull(apiResponse);
		assertTrue(apiResponse.isSuccess());
		assertEquals(new String(bodyBytes), new String(apiResponse.getData().getData()));
		assertEquals(MediaType.get("text/plain"), apiResponse.getData().getContentType());
	}

	@Test
	@DisplayName("Download data - error")
	public void downloadData_error() throws IOException {
		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		when(response.code()).thenReturn(400);
		when(response.isSuccessful()).thenReturn(false);

		GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

		assertNotNull(apiResponse);
		assertFalse(apiResponse.isSuccess());
	}

	@Test
	@DisplayName("Download data - null response body")
	public void downloadData_nullResponseBody() throws IOException {
		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		when(response.code()).thenReturn(200);
		when(response.body()).thenReturn(null);
		when(response.isSuccessful()).thenReturn(true);

		GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

		assertNotNull(apiResponse);
		assertTrue(apiResponse.isSuccess());
		assertNull(apiResponse.getData().getData());
	}

	@Test
	@DisplayName("Download data - null content disposition")
	public void downloadData_nullContentDisposition() throws IOException {
		when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenReturn(response);

		byte[] bodyBytes = "This is answer from test".getBytes();
		InputStream stream = new ByteArrayInputStream(bodyBytes);
		when(response.code()).thenReturn(200);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.byteStream()).thenReturn(stream);
		when(response.body().contentType()).thenReturn(MediaType.get("text/plain"));
		when(response.isSuccessful()).thenReturn(true);
		when(response.header(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(null);

		GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

		assertNotNull(apiResponse);
		assertTrue(apiResponse.isSuccess());
		assertEquals(ATTACHMENT_FILENAME + TARGET_ADDRESS.substring(TARGET_ADDRESS.lastIndexOf("/") + 1), apiResponse.getData().getContentDisposition());
		assertEquals(new String(bodyBytes), new String(apiResponse.getData().getData()));
		assertEquals(MediaType.get("text/plain"), apiResponse.getData().getContentType());
	}

}
