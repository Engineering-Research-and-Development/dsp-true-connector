package it.eng.datatransfer.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.test.context.SpringBootTest;

import it.eng.datatransfer.config.AuthenticatorTestUtil;
import it.eng.datatransfer.config.TestSSLConfiguration;
import it.eng.datatransfer.ftp.client.FTPClient;
import it.eng.datatransfer.ftp.configuration.FTPConfiguration;
import it.eng.datatransfer.server.FTPServerTestUtil;
import it.eng.tools.configuration.GlobalSSLConfiguration;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest(classes = {FTPServerTestUtil.class, AuthenticatorTestUtil.class, TestSSLConfiguration.class})
public class FTPClientTest {
	
	@Autowired
	FTPServerTestUtil serverTestUtil;
	
	@Autowired
	TestSSLConfiguration testSSLConfiguration;
	
	@InjectMocks
	FTPClient ftpClient;
	
	@Mock
	GlobalSSLConfiguration globalSSLConfiguration;
	
	@Mock
	FTPConfiguration ftpConfiguration;
	
	@Mock
	SslBundles sslBundles;
	
	@Mock
	SslBundle bundle;
	
	@Mock
	SslStoreBundle sslStoreBundle;
	
	@Mock
	Certificate certificate;
	
	@Mock
	KeyStore keyStore;
	
	@Mock
	SSLContext sslContext;
	
	@Mock
	SSLSocketFactory sslSocketFactory;

	String bundleName = "connector";
    String alias = "execution-core-container";
    
    @BeforeAll
    public void init () throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, IOException, NoSuchSslBundleException, URISyntaxException  {
    	serverTestUtil.start();
    }
	
	@BeforeEach
	public void cleanSlate() throws UnrecoverableKeyException, KeyStoreException, NoSuchSslBundleException, NoSuchAlgorithmException {
		MockitoAnnotations.openMocks(this);
		configureClient();
	}
	
	
	@Test
	public void downloadFile_ConnectionFailed(@TempDir Path tempDir) {
		when(ftpConfiguration.getDownloadFolder()).thenReturn(tempDir.toString());
		assertThrows(NullPointerException.class, () -> ftpClient.downloadArtifact("test1.csv", "localhost", 5555));
		assertFalse(checkIfFileIsDownloaded(tempDir + "/test1.csv"));
	}
    
	@Test
	public void downloadFile_Successfull(@TempDir Path tempDir) {
		when(ftpConfiguration.getDownloadFolder()).thenReturn(tempDir.toString());
		ftpClient.downloadArtifact("test1.csv", "localhost", 2222);
		assertTrue(checkIfFileIsDownloaded(tempDir + "/test1.csv"));
	}
	
	private void configureClient() throws KeyStoreException, NoSuchSslBundleException, UnrecoverableKeyException, NoSuchAlgorithmException {
		when(sslBundles.getBundle(bundleName)).thenReturn(bundle);
		when(sslBundles.getBundle(bundleName).createSslContext()).thenReturn(sslContext);
		when(sslContext.getSocketFactory()).thenReturn(sslSocketFactory);
		when(sslBundles.getBundle(bundleName).getStores()).thenReturn(sslStoreBundle);
		when(sslBundles.getBundle(bundleName).getStores().getKeyStore()).thenReturn(keyStore);
		when(sslBundles.getBundle(bundleName).getStores().getKeyStore().getCertificate(alias)).thenReturn(certificate);
		when(sslBundles.getBundle(bundleName).getStores().getKeyStore().getCertificate(alias).getPublicKey())
				.thenReturn(testSSLConfiguration.getTlsKeystore().getCertificate(testSSLConfiguration.getTlsKeystoreAlias()).getPublicKey());
		when(sslBundles.getBundle(bundleName).getStores().getKeyStore().getKey(alias, "changeit".toCharArray()))
				.thenReturn(testSSLConfiguration.getTlsKeystore().getKey(testSSLConfiguration.getTlsKeystoreAlias(),testSSLConfiguration.getTlsKeystorePassword().toCharArray()));
		when(globalSSLConfiguration.getKeyPair()).thenReturn(testSSLConfiguration.getKeyPair());
		when(globalSSLConfiguration.getSslBundles()).thenReturn(sslBundles);
		when(globalSSLConfiguration.getSslBundles().getBundle(bundleName).getStores().getKeyStorePassword()).thenReturn(testSSLConfiguration.getTlsKeystorePassword());
		when(globalSSLConfiguration.getPublicKey()).thenReturn(testSSLConfiguration.getTlsKeystore().getCertificate(testSSLConfiguration.getTlsKeystoreAlias()).getPublicKey());
		
		when(ftpConfiguration.getClientUsername()).thenReturn("test_client");
		when(ftpConfiguration.getDefaultTimeoutSeconds()).thenReturn(1000L);
		ftpClient = new FTPClient(globalSSLConfiguration, ftpConfiguration);

	}
	
	private boolean checkIfFileIsDownloaded(String file) {
		File f = new File(file);
		if (f.exists() && !f.isDirectory()) {
			return true;
		}
		return false;
	}
}
