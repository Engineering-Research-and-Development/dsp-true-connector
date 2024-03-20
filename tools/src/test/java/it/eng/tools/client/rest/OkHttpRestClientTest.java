package it.eng.tools.client.rest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@ExtendWith(MockitoExtension.class)
public class OkHttpRestClientTest {
	
	@Mock
	private OkHttpClient okHttpClient;
	@Mock
	private RequestBody formBody;
	@Mock
	private Request request;
	@Mock
	private Call call;
	@Mock
	private Response response;

	@InjectMocks
	private OkHttpRestClient okHttpRestClient;
	
	@Test
	public void callSuccessful() throws IOException {
		when(okHttpClient.newCall(request)).thenReturn(call);
		when(call.execute()).thenReturn(response);
		okHttpRestClient.executeCall(request);
	}
	
	@Test
	public void callError() throws IOException {
		when(okHttpClient.newCall(request)).thenReturn(call);
		when(call.execute()).thenThrow(new IOException("Error"));
		
	    assertNull(okHttpRestClient.executeCall(request));
	}
}
