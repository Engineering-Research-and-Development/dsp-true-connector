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
	
	@Value("${connector.ssl.insecure:true}")
	private boolean insecure;

	private PublicKey publicKey;
	private PrivateKey privateKey;
	private KeyPair keyPair;
	private static final String BUNDLE = "connector";

	public GlobalSSLConfiguration(SslBundles sslBundles) {
		super();
		this.sslBundles = sslBundles;
	}
	
	@PostConstruct
	public void globalSslConfig() {
		if(insecure) {
			log.warn("Configuring INSECURE global SSL context - bypassing all certificate checks");
			setupInsecureSsl();
		} else {
			log.info("Configuring global SSL context - using configured connector key and truststore");
			SSLContext sslContext = sslBundles.getBundle(BUNDLE).createSslContext();
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
		}
		loadKeys();
		loadKeyPair();
	}

	private void setupInsecureSsl() {
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			TrustManager[] trustAllCerts = new TrustManager[] { new InsecureTrustManager() };
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
			HostnameVerifier allHostsValid = (hostname, session) -> true;
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			log.error("Failed to create insecure SSL context", e);
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

	private static class InsecureTrustManager implements X509TrustManager {
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
			return new X509Certificate[0];
		}
	}
}
