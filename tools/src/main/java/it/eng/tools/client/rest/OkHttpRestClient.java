package it.eng.tools.client.rest;

import java.io.IOException;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.model.ExternalData;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.TenantContextHolder;
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

	// HTTP status that triggers a single cached-M2M-credential invalidation + retry, since a 401
	// on an outbound INTERNAL-mode M2M call typically means the cached JWT expired or its signing
	// secret was rotated.
	private static final int HTTP_UNAUTHORIZED = 401;

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
		return sendRequestProtocol(targetAddress, jsonNode, authorization, null);
	}

	/**
	 * Sends a protocol/internal request, optionally scoping it to a specific tenant.
	 * When {@code tenantId} is non-null the {@value TenantContextHolder#HEADER_X_TENANT_ID} header is
	 * added so that {@code ApiTenantContextFilter} activates the correct tenant context on the
	 * receiving thread, ensuring tenant-aware service logic filters by the intended tenant.
	 *
	 * @param targetAddress  destination URL
	 * @param jsonNode       request body (may be {@code null})
	 * @param authorization  full Authorization header value (Basic or Bearer)
	 * @param tenantId       tenant to act on behalf of; {@code null} means super-admin / global
	 * @return GenericApiResponse
	 */
	public GenericApiResponse<String> sendRequestProtocol(String targetAddress, JsonNode jsonNode, String authorization, String tenantId) {
		return executeProtocolRequest(targetAddress, jsonNode, authorization, tenantId).response();
	}

	/**
	 * Sends a protocol/internal request whose Authorization header is produced by {@code
	 * authorizationSupplier}, retrying exactly once if the first attempt is rejected with HTTP 401.
	 *
	 * <p>On a 401, {@link CredentialUtils#invalidateCachedCredentials()} evicts any cached
	 * {@code INTERNAL}-mode M2M token before {@code authorizationSupplier} is invoked a second
	 * time, so the retry presents a freshly minted token. If the retry also returns 401, that
	 * failure is surfaced as-is — no further retries are attempted.
	 *
	 * @param targetAddress          destination URL
	 * @param jsonNode               request body (may be {@code null})
	 * @param authorizationSupplier  supplies the Authorization header value; invoked once, and
	 *                               again on retry after a 401
	 * @return GenericApiResponse
	 */
	public GenericApiResponse<String> sendRequestProtocol(String targetAddress, JsonNode jsonNode,
			Supplier<String> authorizationSupplier) {
		return sendRequestProtocol(targetAddress, jsonNode, authorizationSupplier, null);
	}

	/**
	 * Sends a protocol/internal request whose Authorization header is produced by {@code
	 * authorizationSupplier}, optionally scoped to a tenant, retrying exactly once on HTTP 401 as
	 * described in {@link #sendRequestProtocol(String, JsonNode, Supplier)}.
	 *
	 * @param targetAddress          destination URL
	 * @param jsonNode               request body (may be {@code null})
	 * @param authorizationSupplier  supplies the Authorization header value
	 * @param tenantId               tenant to act on behalf of; {@code null} means super-admin/global
	 * @return GenericApiResponse
	 */
	public GenericApiResponse<String> sendRequestProtocol(String targetAddress, JsonNode jsonNode,
			Supplier<String> authorizationSupplier, String tenantId) {
		ProtocolResult result = executeProtocolRequest(targetAddress, jsonNode, authorizationSupplier.get(), tenantId);
		if (result.statusCode() == HTTP_UNAUTHORIZED) {
			log.info("Received 401 from {} - invalidating cached M2M credentials and retrying once", targetAddress);
			credentialUtils.invalidateCachedCredentials();
			result = executeProtocolRequest(targetAddress, jsonNode, authorizationSupplier.get(), tenantId);
		}
		return result.response();
	}

	// Core implementation shared by every sendRequestProtocol overload; captures the raw HTTP
	// status code (0 on IOException) alongside the API-facing response so retry-on-401 can be
	// decided without expanding GenericApiResponse's public shape to also carry a status code.
	private ProtocolResult executeProtocolRequest(String targetAddress, JsonNode jsonNode, String authorization, String tenantId) {
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
		}
		if (StringUtils.isNotBlank(tenantId)) {
			requestBuilder.addHeader(TenantContextHolder.HEADER_X_TENANT_ID, tenantId);
		}
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
				return new ProtocolResult(code, GenericApiResponse.success(resp, "Response received from " + targetAddress));
			} else {
                return new ProtocolResult(code, GenericApiResponse.error(resp, "Error while making request: " + resp));
			}
		} catch (IOException e) {
			log.error(e.getLocalizedMessage());
			return new ProtocolResult(0, GenericApiResponse.error(e.getLocalizedMessage()));
		}
	}

	// Pairs the raw HTTP status code with the API-facing response; see executeProtocolRequest.
	private record ProtocolResult(int statusCode, GenericApiResponse<String> response) {
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

	/**
	 * Sends an internal (loopback) request authorized via {@link CredentialUtils#getAPICredentials()},
	 * retrying exactly once if the first attempt is rejected with HTTP 401. On a 401, {@link
	 * CredentialUtils#invalidateCachedCredentials()} evicts any cached {@code INTERNAL}-mode M2M
	 * token before {@code getAPICredentials()} is called again, so the retry presents a freshly
	 * minted token. If the retry also returns 401, that failure is surfaced as-is (the caller
	 * receives whatever body the second attempt returned).
	 *
	 * @param contextAddress the request path, appended to the loopback base URL
	 * @param method         the HTTP method
	 * @param jsonBody       request body (may be {@code null})
	 * @return the response body, or {@code null} on an {@link IOException}
	 */
	public String sendInternalRequest(String contextAddress, HttpMethod method, JsonNode jsonBody) {
		
		 String connectorAddress;
			if (sslEnabled) {
				connectorAddress = "https://localhost:";
			} else {
				connectorAddress = "http://localhost:";
			}
		
		String targetAddress = connectorAddress + serverPort + contextAddress;
		// Propagate tenant context so the receiving thread applies correct tenant filtering.
		String tenantId = TenantContextHolder.getTenantId();
		if (StringUtils.isBlank(tenantId)) {
			log.debug("sendInternalRequest: no tenant context set — request will run as super-admin");
		}

		InternalResult result = executeInternalRequest(targetAddress, method, jsonBody,
				credentialUtils.getAPICredentials(), tenantId);
		if (result.statusCode() == HTTP_UNAUTHORIZED) {
			log.info("Received 401 from {} - invalidating cached M2M credentials and retrying once", targetAddress);
			credentialUtils.invalidateCachedCredentials();
			result = executeInternalRequest(targetAddress, method, jsonBody,
					credentialUtils.getAPICredentials(), tenantId);
		}
		return result.body();
	}

	// Core implementation shared by both attempts of sendInternalRequest; captures the raw HTTP
	// status code (0 on IOException) alongside the response body so retry-on-401 can be decided.
	private InternalResult executeInternalRequest(String targetAddress, HttpMethod method, JsonNode jsonBody,
			String authorization, String tenantId) {
		Request.Builder requestBuilder = new Request.Builder()
				.url(targetAddress);
		requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, authorization);
		if (StringUtils.isNotBlank(tenantId)) {
			requestBuilder.addHeader(TenantContextHolder.HEADER_X_TENANT_ID, tenantId);
		}
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
			return new InternalResult(code, resp);
        } catch (IOException e) {
			log.error(e.getLocalizedMessage());
			return new InternalResult(0, null);
		}
	}

	// Pairs the raw HTTP status code with the response body; see executeInternalRequest.
	private record InternalResult(int statusCode, String body) {
	}
}
