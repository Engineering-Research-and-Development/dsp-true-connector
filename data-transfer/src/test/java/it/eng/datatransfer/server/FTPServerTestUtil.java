package it.eng.datatransfer.server;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import it.eng.datatransfer.config.AuthenticatorTestUtil;
import it.eng.datatransfer.config.TestSSLConfiguration;
import it.eng.datatransfer.event.StartFTPServerEvent;
import it.eng.datatransfer.ftp.configuration.FTPConfiguration;
import it.eng.datatransfer.ftp.server.FTPAuthenticator;
import it.eng.datatransfer.ftp.server.FTPServer;
import it.eng.tools.configuration.GlobalSSLConfiguration;


@TestComponent
@ExtendWith(SpringExtension.class)
public class FTPServerTestUtil {
	
	@Autowired
	AuthenticatorTestUtil authenitcatorTestUtil;
	
	@Autowired
	TestSSLConfiguration testSSLConfiguration;

	@Mock
	GlobalSSLConfiguration globalSSLConfiguration;
	
	@Mock
	FTPAuthenticator authenticator;
	
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
	
	public void start() throws IOException, URISyntaxException, KeyStoreException, NoSuchSslBundleException, UnrecoverableKeyException, NoSuchAlgorithmException {
		MockitoAnnotations.openMocks(this);
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
		
		when(ftpConfiguration.getHost()).thenReturn("localhost");
		when(ftpConfiguration.getServerUsername()).thenReturn("test_server");
		when(ftpConfiguration.getServerPort()).thenReturn(2222);
		when(ftpConfiguration.getDefaultTimeoutSeconds()).thenReturn(1000L);
		when(ftpConfiguration.getServerFolder()).thenReturn("src/test/resources/ftp_server");
		new FTPServer(globalSSLConfiguration, authenitcatorTestUtil, ftpConfiguration).start(new StartFTPServerEvent());
		
	}
	
}
