package it.eng.datatransfer.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.KeyStoreException;
import java.security.PublicKey;
import java.util.Iterator;

@Slf4j
@TestComponent
@ExtendWith(SpringExtension.class)
public class AuthenticatorTestUtil implements PublickeyAuthenticator {
	@Autowired
	TestSSLConfiguration sslConfiguration;

	@Override
	public boolean authenticate(String username, PublicKey key, ServerSession session) throws AsyncAuthException {
		boolean isAuthenticated = false;
		Iterator<String> aliases = null;
		try {
			aliases = sslConfiguration.getTrustManagerKeyStore().aliases().asIterator();
			while (aliases.hasNext() && !isAuthenticated) {
				isAuthenticated = KeyUtils.compareKeys(key,
						sslConfiguration.getTrustManagerKeyStore().getCertificate(aliases.next()).getPublicKey());
			}
		} catch (KeyStoreException e) {
			log.error("Problem with Truststore: " + e.getMessage());
		} catch (NoSuchSslBundleException e) {
			log.error("SSL error occurred: " + e.getMessage());
		}
		return isAuthenticated;
	}
}
