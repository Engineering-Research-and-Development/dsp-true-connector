package it.eng.tools.client.rest;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
@Slf4j
public class OkHttpRestClient {

	private OkHttpClient okHttpClient;
	
	public OkHttpRestClient(@Qualifier("okHttpClient") OkHttpClient okHttpClient) {
		this.okHttpClient = okHttpClient;
	}
	
	public Response executeCall(Request request) {
		try {
			return okHttpClient.newCall(request).execute();
		} catch (IOException e) {
			log.error("Error while executing rest call", e);
			//TODO add error handler for REST calls
		}
		return null;
	}
	
	/**
	 * Sends protocol request
	 * @param targetAddress
	 * @param jsonNode
	 * @param authorization
	 * @return
	 */
	public GenericApiResponse<String> sendRequestProtocol(String targetAddress, JsonNode jsonNode, String authorization) {
		// send response to targetAddress
		RequestBody body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
		Request.Builder requestBuilder = new Request.Builder()
			      .url(targetAddress)
			      .post(body);
		if(StringUtils.isNotBlank(authorization)) {
			requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, authorization);
		}
		Request request = requestBuilder.build();
		log.info("Sending request using address: " + targetAddress);
		try (Response response = okHttpClient.newCall(request).execute()) {
			int code = response.code();
			log.info("Status {}", code);
			//why is this not JSONNode
			String resp = response.body().string();
			log.info("Response received: {}", resp);
			if(response.isSuccessful()) { // code in 200..299
				return GenericApiResponse.success(resp, "Response received from " + targetAddress, code);
			} else {
				return GenericApiResponse.error(resp, code);
			}
        } catch (IOException e) {
			log.error(e.getLocalizedMessage());
			return GenericApiResponse.error(e.getLocalizedMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
		}
	}
}
