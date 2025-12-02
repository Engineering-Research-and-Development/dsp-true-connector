package it.eng.tools.configuration;

import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@Getter
public class GlobalSSLConfiguration {

	private final SslBundles sslBundles;
	private final boolean isSSLEnabled;

	private PublicKey publicKey;
	private PrivateKey privateKey;
	private KeyPair keyPair;
	private final String BUNDLE = "connector";

	public GlobalSSLConfiguration(SslBundles sslBundles,
								  @Value("${server.ssl.enabled:false}") boolean isSSLEnabled) {
		super();
		this.sslBundles = sslBundles;
		this.isSSLEnabled = isSSLEnabled;
	}
	
	@PostConstruct
	public void globalSslConfig() {
		if (!isSSLEnabled) {
			log.warn("Configuring INSECURE global SSL context (server.ssl.enabled=false)");
			log.warn("HttpsURLConnection will accept ALL certificates without validation - use only in development!");
			configureInsecureSSL();
		} else {
			log.info("Configuring SECURE global SSL context - using configured connector key and truststore");
			configureSecureSSL();
			loadKeys();
			loadKeyPair();
		}
	}

	/**
	 * Configures secure SSL using the configured truststore and keystore.
	 * This is the production configuration with proper certificate validation.
	 */
	private void configureSecureSSL() {
		try {
			SSLContext sslContext = sslBundles.getBundle(BUNDLE).createSslContext();
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
			// Use default hostname verifier (strict validation)
			log.info("Secure SSL context configured with hostname verification enabled");
		} catch (NoSuchSslBundleException e) {
			log.error("Could not configure secure SSL context - bundle '{}' not found", BUNDLE, e);
		}
	}

	/**
	 * Configures insecure SSL that accepts all certificates and hostnames.
	 * This should only be used in development or testing environments.
	 * WARNING: This disables all SSL/TLS security validations!
	 */
	private void configureInsecureSSL() {
		try {
			// Create a trust manager that accepts all certificates
			TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					@Override
					public void checkClientTrusted(X509Certificate[] chain, String authType) {
						// Accept all client certificates
					}

					@Override
					public void checkServerTrusted(X509Certificate[] chain, String authType) {
						// Accept all server certificates
					}

					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[]{};
					}
				}
			};

			// Create SSL context with trust-all manager
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

			// Set as default for HttpsURLConnection
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

			// Create a hostname verifier that accepts all hostnames
			HostnameVerifier allHostsValid = (hostname, session) -> {
				log.trace("Accepting hostname: {} (insecure mode)", hostname);
				return true;
			};
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

			log.warn("Insecure SSL context configured - ALL certificates and hostnames will be accepted!");
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			log.error("Failed to configure insecure SSL context", e);
		}
	}

	private void loadKeys() {
		try {
			log.info("Loading public/private key from connector sslBundle");
			publicKey = sslBundles.getBundle(BUNDLE).getStores().getKeyStore().getCertificate(sslBundles.getBundle(BUNDLE).getKey().getAlias())
					.getPublicKey();
			privateKey = (PrivateKey) sslBundles.getBundle(BUNDLE).getStores().getKeyStore()
					.getKey(sslBundles.getBundle(BUNDLE).getKey().getAlias(), sslBundles.getBundle(BUNDLE).getKey().getPassword().toCharArray());
		} catch (KeyStoreException | NoSuchSslBundleException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
			log.error("Could not load public/private key from connector sslBundle", e);
		}
	}
	
	private void loadKeyPair() {
		log.info("Creating keypair from SSLBundle - connector");
		keyPair = new KeyPair(getPublicKey(), getPrivateKey());
	}

}
