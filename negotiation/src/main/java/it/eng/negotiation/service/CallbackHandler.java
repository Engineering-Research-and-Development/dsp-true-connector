package it.eng.negotiation.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * Sends a response to a specified callback address using HTTP POST method if callback address is not null or empty.
     *
     * @param callbackAddress - the URL to which the response needs to be sent.
     * @param jsonNode        - the JSON content to be sent as the body of the request.
     */
	public void handleCallbackResponse(String callbackAddress, JsonNode jsonNode) {
		if(!ObjectUtils.isEmpty(callbackAddress)) {
			// send response to callback URL
			RequestBody body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
			Request request = new Request.Builder()
				      .url(callbackAddress)
				      .addHeader("Authorization", okhttp3.Credentials.basic("milisav@mail.com", "password"))
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
	
	/**
	 * 
	 * @param callbackAddress
	 * @param jsonNode
	 * @return HTTP status code of the request
	 */
	public int handleCallbackResponseProtocol(String callbackAddress, JsonNode jsonNode) {
		if(!ObjectUtils.isEmpty(callbackAddress)) {
			// send response to callback URL
//			okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonNode.toPrettyString(), okhttp3.MediaType.parse(MediaType.APPLICATION_JSON_VALUE));
			RequestBody body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
			
			Request request = new Request.Builder()
				      .url(callbackAddress)
				      .addHeader("Authorization", okhttp3.Credentials.basic("milisav@mail.com", "password"))
				      .post(body)
				      .build();
			log.info("Sending response using callback address: " + callbackAddress);
			try (Response response = client.executeCall(request)) {
				log.info("Status {}", response.code());
	            log.info("Response received: {}", response.body().string());
	            return response.code();
	        } catch (IOException e) {
				log.error(e.getLocalizedMessage());
				return 500;
			}
		} 
		return 0;
	}
	
	
	public String sendRequestProtocol(String callbackAddress, JsonNode jsonNode) {
		if(!ObjectUtils.isEmpty(callbackAddress)) {
			// send response to callback URL
//			okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonNode.toPrettyString(), okhttp3.MediaType.parse(MediaType.APPLICATION_JSON_VALUE));
			RequestBody body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
			
			Request request = new Request.Builder()
				      .url(callbackAddress)
				      .addHeader("Authorization", okhttp3.Credentials.basic("milisav@mail.com", "password"))
				      .post(body)
				      .build();
			log.info("Sending response using callback address: " + callbackAddress);
			try (Response response = client.executeCall(request)) {
				log.info("Status {}", response.code());
				String resp = response.body().string();
	            log.info("Response received: {}", resp);
	            return resp;
	        } catch (IOException e) {
				log.error(e.getLocalizedMessage());
				return null;
			}
		} 
		return null;
	}
}
