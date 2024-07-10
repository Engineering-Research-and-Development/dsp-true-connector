package it.eng.tools.configuration;

import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

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
	
	private PublicKey publicKey;
	private PrivateKey privateKey;
	private KeyPair keyPair;
	private String BUNDLE = "connector";

	public GlobalSSLConfiguration(SslBundles sslBundles) {
		super();
		this.sslBundles = sslBundles;
	}
	
	@PostConstruct
	public void globalSslConfig() {
		log.info("Configuring global SSL context - using configured connector key and truststore");
		SSLContext sslContext = sslBundles.getBundle(BUNDLE).createSslContext();
		HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
		loadKeys();
		loadKeyPair();
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
