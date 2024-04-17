package it.eng.negotiation.service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.client.rest.OkHttpRestClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
@Slf4j
public class CallbackHandler {
	
//	@Qualifier("okHttpClientInsecure")
	@Autowired
	private OkHttpRestClient client;
	
//	public CallbackHandler(@Qualifier("okHttpClientInsecure") OkHttpRestClient okHttpClientInsecure) {
//		super();
//		this.client = okHttpClientInsecure;
//	}

	@Async("asyncExecutor")
	public void handleCallbackResponse(String callbackAddress, Future<JsonNode> jsonNodeFuture) throws InterruptedException, ExecutionException {
		if(!ObjectUtils.isEmpty(callbackAddress)) {
			// send response to callback URL
			RequestBody body = RequestBody.create(jsonNodeFuture.get().toPrettyString(), MediaType.parse("application/json"));
			Request request = new Request.Builder()
				      .url(callbackAddress)
				      .addHeader("Authorization", okhttp3.Credentials.basic("username", "password"))
				      .post(body)
				      .build();
			log.info("Sending response using callback address: " + callbackAddress);
			try (Response response = client.executeCall(request)) {
	            log.info("Response received: " + response.body().string());
	        } catch (IOException e) {
				log.error(e.getLocalizedMessage());
			}
		} 
	}
	
	@Async("asyncExecutor")
	public CompletableFuture<String> handleCallbackResponseProtocol(String callbackAddress, JsonNode jsonNode) {
		if(!ObjectUtils.isEmpty(callbackAddress)) {
			// send response to callback URL
//			okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonNode.toPrettyString(), okhttp3.MediaType.parse(MediaType.APPLICATION_JSON_VALUE));
			RequestBody body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
			
			Request request = new Request.Builder()
				      .url(callbackAddress)
				      .addHeader("Authorization", okhttp3.Credentials.basic("username", "password"))
				      .post(body)
				      .build();
			log.info("Sending response using callback address: " + callbackAddress);
			try (Response response = client.executeCall(request)) {
				log.info("Status {}", response.code());
	            log.info("Response received: {}", response.body().string());
	            return CompletableFuture.completedFuture(response.body().string());
	        } catch (IOException e) {
				log.error(e.getLocalizedMessage());
				return CompletableFuture.failedFuture(e);
			}
		} 
		return null;
	}
}
