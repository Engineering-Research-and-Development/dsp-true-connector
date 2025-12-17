package it.eng.tools.client.rest;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.model.ExternalData;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
@Slf4j
public class OkHttpRestClient {
	
	private final String serverPort;
	private final boolean sslEnabled;
	private final OkHttpClient okHttpClient;
	private final CredentialUtils credentialUtils;
	private static final String ATTACHMENT_FILENAME = "attachment;filename=";

	public OkHttpRestClient(OkHttpClient okHttpClient, CredentialUtils credentialUtils,
			@Value("${server.port}") String serverPort, @Value("${server.ssl.enabled}") boolean sslEnabled) {
		this.okHttpClient = okHttpClient;
		this.credentialUtils = credentialUtils;
		this.serverPort = serverPort;
		this.sslEnabled = sslEnabled;
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
	 * Sends protocol request.
	 * @param targetAddress protocol address
	 * @param jsonNode request body
	 * @param authorization full authorization header e.g. Bearer token
	 * @return GenericApiResponse
	 */
	public GenericApiResponse<String> sendRequestProtocol(String targetAddress, JsonNode jsonNode, String authorization) {
		// send response to targetAddress
		Request.Builder requestBuilder = new Request.Builder().url(targetAddress);
        RequestBody body;
        if(jsonNode != null) {
            body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
        } else {
            body = RequestBody.create("", MediaType.parse("application/json"));
        }
        requestBuilder.post(body);
        if(StringUtils.isNotBlank(authorization)) {
			requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, authorization);
		} else {
			// If no authorization provided, get credentials with target URL for dynamic DID extraction
			String credentials = credentialUtils.getConnectorCredentials(targetAddress);
			if(StringUtils.isNotBlank(credentials)) {
				requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, credentials);
				log.debug("Added dynamic authorization header for target: {}", targetAddress);
			}
		}
		Request request = requestBuilder.build();
        log.info("Sending request using address: {}", targetAddress);
		try (Response response = okHttpClient.newCall(request).execute()) {
			int code = response.code();
			log.info("Status {}", code);
			//why is this not JSONNode
            String resp = null;
            if (response.body() != null) {
                resp = response.body().string();
            }
            log.info("Response received: {}", resp);
			if(response.isSuccessful()) { // code in 200..299
				return GenericApiResponse.success(resp, "Response received from " + targetAddress);
			} else {
                return GenericApiResponse.error(resp, "Error while making request: " + resp);
			}
		} catch (IOException e) {
			log.error(e.getLocalizedMessage());
			return GenericApiResponse.error(e.getLocalizedMessage());
		}
	}
	
	/**
	 * Sends GET request.
	 * @param targetAddress request address
	 * @param authorization full authorization header e.g. Bearer token
	 * @return GenericApiResponse
	 */
	public GenericApiResponse<String> sendGETRequest(String targetAddress, String authorization) {
		Request.Builder requestBuilder = new Request.Builder().url(targetAddress);
		if(StringUtils.isNotBlank(authorization)) {
			requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, authorization);
		}
		requestBuilder.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		Request request = requestBuilder.build();
		log.info("Sending request using address: {}", targetAddress);
		try (Response response = okHttpClient.newCall(request).execute()) {
			int code = response.code();
			log.info("Status {}", code);
            String resp = null;
            if (response.body() != null) {
                resp = response.body().string();
            }
            log.info("Response received: {}", resp);
			if(response.isSuccessful()) { // code in 200..299
				return GenericApiResponse.success(resp, "Response received from " + targetAddress);
			} else {
				return GenericApiResponse.error(resp);
			}
        } catch (IOException e) {
			log.error(e.getLocalizedMessage());
			return GenericApiResponse.error(e.getLocalizedMessage());
		}
	}
	
	/**
	 * Sends GET request to download data.
	 * @param targetAddress request address
	 * @param authorization full authorization header e.g. Bearer token
	 * @return GenericApiResponse
	 */
	public GenericApiResponse<ExternalData> downloadData(String targetAddress, String authorization) {
		Request.Builder requestBuilder = new Request.Builder()
				.url(targetAddress);
		if(StringUtils.isNotBlank(authorization)) {
			requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, authorization);
		}
		Request request = requestBuilder.build();
        log.info("Sending request using address: {}", targetAddress);
		try (Response response = okHttpClient.newCall(request).execute()) {
			int code = response.code();
			log.info("Status {}", code);
			if(response.isSuccessful()) { // code in 200..299
				ExternalData externalData = new ExternalData();
                if (response.body() != null) {
                    externalData.setData(response.body().bytes());
					externalData.setContentType(response.body().contentType());
				}

				String contentDisposition = response.header(HttpHeaders.CONTENT_DISPOSITION);
				if (contentDisposition == null) {
				    contentDisposition = ATTACHMENT_FILENAME + targetAddress.substring(targetAddress.lastIndexOf('/') + 1);
				}
				externalData.setContentDisposition(contentDisposition);
				return GenericApiResponse.success(externalData, "Response received from " + targetAddress);
			} else {
				return GenericApiResponse.error(response.message());
			}
        } catch (IOException e) {
			log.error(e.getLocalizedMessage());
			return GenericApiResponse.error(e.getLocalizedMessage());
        }
	}

	public String sendInternalRequest(String contextAddress, HttpMethod method, JsonNode jsonBody) {
		
		 String connectorAddress;
			if (sslEnabled) {
				connectorAddress = "https://localhost:";
			} else {
				connectorAddress = "http://localhost:";
			}
		
		String targetAddress = connectorAddress + serverPort + contextAddress;
		Request.Builder requestBuilder = new Request.Builder()
				.url(targetAddress);
		requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, credentialUtils.getAPICredentials());
		if(HttpMethod.GET.equals(method)) {
			// performing get
			requestBuilder.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		} else {
            RequestBody body;
            if(jsonBody != null) {
                body = RequestBody.create(jsonBody.toPrettyString(), MediaType.parse("application/json"));
            } else {
                body = RequestBody.create("", MediaType.parse("application/json"));
            }
            requestBuilder.post(body);
        }
		Request request = requestBuilder.build();
		try (Response response = okHttpClient.newCall(request).execute()) {
			int code = response.code();
			log.info("Status {}", code);
            String resp = null;
            if (response.body() != null) {
                resp = response.body().string();
            }
            log.info("Response received: {}", resp);
			// TODO see to pass GenericApiResponse<X> as parameter and then 
			// TypeReference<GenericApiResponse<List<String>>> typeRef = new TypeReference<GenericApiResponse<List<String>>>() {};
			// GenericApiResponse<List<String>> apiResp =  objectMapper.readValue(resp, typeRef);
			return resp;
        } catch (IOException e) {
			log.error(e.getLocalizedMessage());
			return null;
		}
	}
}
