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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.internal.tls.OkHostnameVerifier;

@Configuration
@Slf4j
public class OkHttpClientConfiguration {

	@Autowired
	private SslBundles sslBundles;
	
	@Bean
	@Primary
	public OkHttpClient okHttpClient() throws KeyStoreException, NoSuchSslBundleException, KeyManagementException, NoSuchAlgorithmException {
//		log.info("REGUALR OK HTTP CLIENT");
//		sslBundles.getBundle("connector").getStores().getKeyStore().getCertificate("execution-core-container");
//		sslBundles.getBundle("connector").getStores().getTrustStore().aliases();
//		
//		TrustManager[] trustManagers = sslBundles.getBundle("connector").getManagers().getTrustManagers();
//		SSLContext sslContext = sslBundles.getBundle("connector").createSslContext();
//		
//		OkHttpClient client;
//		//@formatter:off
//		client = new OkHttpClient.Builder()
//				.connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
//				.connectTimeout(60, TimeUnit.SECONDS)
//		        .writeTimeout(60, TimeUnit.SECONDS)
//		        .readTimeout(60, TimeUnit.SECONDS)
//		        .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0])
//		        .hostnameVerifier(OkHostnameVerifier.INSTANCE)
//		        .build();
//		//@formatter:on
//		return client;
		return okHttpClientInsecure();
	}
	
//	@Bean
	//("okHttpClientInsecure")
	private OkHttpClient okHttpClientInsecure() throws NoSuchAlgorithmException, KeyManagementException {
		log.info("NON SECURE OK HTTP CLIENT");
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
		SSLContext sslContextTrustAllCerts = SSLContext.getInstance("SSL");
		sslContextTrustAllCerts.init(null, trustAllCerts, new java.security.SecureRandom());
				
		OkHttpClient client;
		//@formatter:off
		client = new OkHttpClient.Builder()
//				.connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
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
