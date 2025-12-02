package it.eng.tools.configuration;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import it.eng.tools.ssl.ocsp.OcspTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.internal.tls.OkHostnameVerifier;

@Configuration
@Slf4j
public class OkHttpClientConfiguration {

	private final OcspTrustManagerFactory ocspTrustManagerFactory;
	private final boolean isSSLEnabled;

	public OkHttpClientConfiguration(OcspTrustManagerFactory ocspTrustManagerFactory,
									 @Value("${server.ssl.enabled:false}") boolean isSSLEnabled) {
		super();
		this.ocspTrustManagerFactory = ocspTrustManagerFactory;
		this.isSSLEnabled = isSSLEnabled;
	}

	@Bean
	@Primary
	OkHttpClient okHttpClient() throws KeyStoreException, NoSuchSslBundleException, KeyManagementException, NoSuchAlgorithmException {
		if (!isSSLEnabled) {
			log.warn("Creating insecure OkHttpClient (server.ssl.enabled=false)");
			log.warn("This client will accept ALL certificates without validation - use only in development!");
			return okHttpClientInsecure();
		} else {
			log.info("Creating secure OkHttpClient with OCSP validation (server.ssl.enabled=true)");
			return okHttpClientWithOcspValidation();
		}
	}
	
	/**
	 * Creates an OkHttpClient with OCSP validation.
	 * 
	 * @return OkHttpClient with OCSP validation
	 * @throws KeyStoreException If there's an error accessing the key store
	 * @throws NoSuchAlgorithmException If the algorithm is not available
	 * @throws KeyManagementException If there's an error managing keys
	 */
	private OkHttpClient okHttpClientWithOcspValidation() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
		log.info("Creating secured OkHttpClient - remote certificates will be validated");

		// Create OCSP-enabled trust managers
		TrustManager[] trustManagers = ocspTrustManagerFactory.createTrustManagers();
		log.debug("Created {} trust manager(s) from OCSP factory", trustManagers.length);

		if (trustManagers.length > 0 && trustManagers[0] instanceof X509TrustManager) {
			X509TrustManager x509TrustManager = (X509TrustManager) trustManagers[0];
			int acceptedIssuersCount = x509TrustManager.getAcceptedIssuers() != null
				? x509TrustManager.getAcceptedIssuers().length : 0;
			log.info("Trust manager has {} accepted issuer(s) in truststore", acceptedIssuersCount);
		}

		// Create SSL context with OCSP-enabled trust managers
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustManagers, new java.security.SecureRandom());
		log.debug("SSLContext initialized with TLS protocol");

		// Create OkHttpClient with OCSP validation
		OkHttpClient client;
		//@formatter:off
		client = new OkHttpClient.Builder()
				.connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
				.connectTimeout(60, TimeUnit.SECONDS)
		        .writeTimeout(60, TimeUnit.SECONDS)
		        .readTimeout(60, TimeUnit.SECONDS)
		        .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0])
		        .hostnameVerifier(OkHostnameVerifier.INSTANCE)
		        .build();
		//@formatter:on
		
		log.info("Secure OkHttpClient created successfully with hostname verification enabled");
		return client;
	}
	
	/**
	 * Creates an insecure OkHttpClient that accepts all certificates without validation.
	 * This is kept for backward compatibility and should only be used in development or testing.
	 * 
	 * @return Insecure OkHttpClient
	 * @throws NoSuchAlgorithmException If the algorithm is not available
	 * @throws KeyManagementException If there's an error managing keys
	 */
	private OkHttpClient okHttpClientInsecure() throws NoSuchAlgorithmException, KeyManagementException {
		log.warn("Creating NON SECURE OK HTTP CLIENT - This should only be used in development or testing");
		TrustManager[] trustAllCerts = new TrustManager[]{
			    new X509TrustManager() {
			        @Override
			        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
			        }

			        @Override
			        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
			        }

			        @Override
			        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			            return new java.security.cert.X509Certificate[]{};
			        }
			    }
			};
		SSLContext sslContextTrustAllCerts = SSLContext.getInstance("TLS");
		sslContextTrustAllCerts.init(null, trustAllCerts, new java.security.SecureRandom());
				
		OkHttpClient client;
		//@formatter:off
		client = new OkHttpClient.Builder()
				.connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
				.connectTimeout(60, TimeUnit.SECONDS)
		        .writeTimeout(60, TimeUnit.SECONDS)
		        .readTimeout(60, TimeUnit.SECONDS)
		        .sslSocketFactory(sslContextTrustAllCerts.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
		        .hostnameVerifier((hostname, session) -> true)
		        .build();
		//@formatter:on
		return client;
	}
}
