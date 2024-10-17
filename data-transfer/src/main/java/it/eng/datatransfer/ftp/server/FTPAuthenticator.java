package it.eng.datatransfer.ftp.server;

import java.security.KeyStoreException;
import java.security.PublicKey;
import java.util.Iterator;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.stereotype.Component;

import it.eng.tools.configuration.GlobalSSLConfiguration;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Qualifier(value = "FTPAuthenticator")
public class FTPAuthenticator implements PublickeyAuthenticator {

	private final GlobalSSLConfiguration sslConfiguration;

	public FTPAuthenticator(GlobalSSLConfiguration sslConfiguration) {
		super();
		this.sslConfiguration = sslConfiguration;
	}

	@Override
	public boolean authenticate(String username, PublicKey key, ServerSession session) throws AsyncAuthException {
		boolean isAuthenticated = false;
		Iterator<String> aliases = null;
		try {
			aliases = sslConfiguration.getSslBundles().getBundle("connector").getStores().getTrustStore().aliases()
					.asIterator();
			while (aliases.hasNext() && !isAuthenticated) {
				isAuthenticated = KeyUtils.compareKeys(key, 
						sslConfiguration.getSslBundles().getBundle("connector").getStores().getTrustStore().getCertificate(aliases.next()).getPublicKey());
			}
		} catch (KeyStoreException e) {
			log.error("Problem with Truststore: ", e.getMessage());
		} catch (NoSuchSslBundleException e) {
			log.error("SSL error occurred: ", e.getMessage());;
		}
		return isAuthenticated;
	}

}
