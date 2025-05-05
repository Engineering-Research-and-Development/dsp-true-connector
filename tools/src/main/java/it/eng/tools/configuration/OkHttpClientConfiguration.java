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

import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import it.eng.tools.ssl.ocsp.OcspProperties;
import it.eng.tools.ssl.ocsp.OcspTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.internal.tls.OkHostnameVerifier;

@Configuration
@Slf4j
public class OkHttpClientConfiguration {

	private final OcspTrustManagerFactory ocspTrustManagerFactory;
	private final OcspProperties ocspProperties;
	
	public OkHttpClientConfiguration(OcspTrustManagerFactory ocspTrustManagerFactory, OcspProperties ocspProperties) {
		super();
		this.ocspTrustManagerFactory = ocspTrustManagerFactory;
		this.ocspProperties = ocspProperties;
	}

	@Bean
	@Primary
	OkHttpClient okHttpClient() throws KeyStoreException, NoSuchSslBundleException, KeyManagementException, NoSuchAlgorithmException {
		if (ocspProperties.isEnabled()) {
			log.info("Creating OkHttpClient with OCSP validation");
			return okHttpClientWithOcspValidation();
		} else {
			log.info("OCSP validation is disabled, using insecure OkHttpClient");
			return okHttpClientInsecure();
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
		log.info("Creating OkHttpClient with OCSP validation");
		
		// Create OCSP-enabled trust managers
		TrustManager[] trustManagers = ocspTrustManagerFactory.createTrustManagers();
		
		// Create SSL context with OCSP-enabled trust managers
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustManagers, new java.security.SecureRandom());
		
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
