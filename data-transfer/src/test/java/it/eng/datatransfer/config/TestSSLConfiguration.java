package it.eng.datatransfer.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@TestComponent
@ExtendWith(SpringExtension.class)
public class TestSSLConfiguration {
	
	String tlsKeystoreName = "ssl-server.jks";
	String tlsKeystorePassword = "changeit";
	String tlsKeystoreAlias = "execution-core-container";
	Path targetDirectory = Path.of("src", "test", "resources");
	String trustStoreName = "truststoreEcc.jks";
	String trustStorePwd = "allpassword";

	private KeyStore tlsKeystore;
	private KeyStore trustManagerKeyStore;
	private KeyManagerFactory keyFactory;
	private TrustManagerFactory trustFactory;
	private KeyPair keyPair;
	
	
	public TestSSLConfiguration() {
		super();
		loadTLSKeystore(tlsKeystoreName, tlsKeystorePassword, tlsKeystoreAlias, targetDirectory);
		loadTrustStore(targetDirectory, trustStoreName, trustStorePwd);
		loadKeyPair();
	}

	private void loadTLSKeystore(String tlsKeystoreName, String tlsKeystorePassword, String tlsKeystoreAlias,
			Path targetDirectory) {
		log.info("Loading TLS keystore: " + tlsKeystoreName);
		try (InputStream jksKeyStoreInputStream = Files.newInputStream(targetDirectory.resolve(tlsKeystoreName))) {
			tlsKeystore = KeyStore.getInstance("JKS");
			tlsKeystore.load(jksKeyStoreInputStream, tlsKeystorePassword.toCharArray());
			keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyFactory.init(tlsKeystore, tlsKeystorePassword.toCharArray());
		} catch (UnrecoverableKeyException | IOException | KeyStoreException | NoSuchAlgorithmException
				| CertificateException e) {
			log.error("Error while trying to read server certificate", e);
		}
	}

	private void loadTrustStore(Path targetDirectory, String trustStoreName, String trustStorePwd) {
		log.info("Loading truststore: " + trustStoreName);
		try (InputStream jksTrustStoreInputStream = Files.newInputStream(targetDirectory.resolve(trustStoreName))) {
			trustManagerKeyStore = KeyStore.getInstance("JKS");
			trustManagerKeyStore.load(jksTrustStoreInputStream, trustStorePwd.toCharArray());
			trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustFactory.init(trustManagerKeyStore);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
			log.error("Error while trying to read server truststore", e);
		}
	}
	
	private void loadKeyPair() {
		log.info("Creating keypair");
		PublicKey publicKey = null;
		PrivateKey key = null;
		try {
			publicKey = getTlsKeystore().getCertificate(getTlsKeystoreAlias()).getPublicKey();
			key = (PrivateKey) getTlsKeystore().getKey(getTlsKeystoreAlias(), getTlsKeystorePassword().toCharArray());
		} catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
			log.error("Error while trying to load keypair", e);
		}
		keyPair = new KeyPair(publicKey, key);
	}


}
