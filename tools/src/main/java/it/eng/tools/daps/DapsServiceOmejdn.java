package it.eng.tools.daps;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.eng.tools.client.rest.OkHttpRestClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.FormBody.Builder;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
@Slf4j
public class DapsServiceOmejdn implements DapsService {

	private DapsProperties dapsProperties;
	private DapsCertificateProviderOmejdn dapsCertificateProvider;
	private OkHttpRestClient client;

	public DapsServiceOmejdn(DapsProperties dapsProperties, DapsCertificateProviderOmejdn dapsCertificateProvider,
			OkHttpRestClient client) {
		this.dapsProperties = dapsProperties;
		this.dapsCertificateProvider = dapsCertificateProvider;
		this.client = client;
	}

	@Override
	public String fetchToken() {
		String token = null;
		Response jwtResponse = null;
		try {
			log.info("Retrieving Dynamic Attribute Token...");

			String jws = dapsCertificateProvider.getDapsV2Jws();

			// build form body to embed client assertion into post request
			Builder formBodyBuilder = new FormBody.Builder()
					.add("grant_type", "client_credentials")
					.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
					.add("client_assertion", jws)
					.add("scope", "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL");

//			if(extendedTokenValidation) { 
//				String certsShaClaim = createCertsShaClaim();
//				formBodyBuilder.add("claims", certsShaClaim);
//			}

			RequestBody formBody = formBodyBuilder.build();
			Request request = new Request.Builder().url(dapsProperties.getDapsUrl()).post(formBody).build();
			jwtResponse = client.executeCall(request);
			if (!jwtResponse.isSuccessful()) {
				throw new IOException("Unexpected code " + jwtResponse);
			}
			var responseBody = jwtResponse.body();
			if (responseBody == null) {
				throw new Exception("JWT response is null.");
			}
			var jwtString = responseBody.string();
			ObjectNode node = new ObjectMapper().readValue(jwtString, ObjectNode.class);

			if (node.has("access_token")) {
				token = node.get("access_token").asText();
			}
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException e) {
			log.error("Cannot acquire token:", e);
		} catch (IOException e) {
			log.error("Error retrieving token:", e);
		} catch (Exception e) {
			log.error("Something else went wrong:", e);
		} finally {
			if (jwtResponse != null) {
				jwtResponse.close();
			}
		}
		return token;
	}

	@Override
	public boolean validateToken(String token) {
		boolean valid = false;
		if (token == null) {
			log.error("Token is null");
			return valid;
		}
		try {
			DecodedJWT jwt = JWT.decode(token);
			Algorithm algorithm = dapsProperties.getAlogirthm(jwt);
			algorithm.verify(jwt);
			valid = true;
			if (jwt.getExpiresAt().before(new Date())) {
				valid = false;
				log.warn("Token expired");
			}
//			if(extendedTokenValidation) {
//				if(!extendedTokenValidation(jwt)) {
//					valid = false;
//				}
//			}
		} catch (SignatureVerificationException | NullPointerException e) {
			log.info("Token did not get verified, {}", e);
		} catch (JWTDecodeException e) {
			log.error("Invalid token, {}", e);
		}
		return valid;
	}

}
