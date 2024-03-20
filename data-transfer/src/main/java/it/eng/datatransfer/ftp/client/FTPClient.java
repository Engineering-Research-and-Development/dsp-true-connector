package it.eng.datatransfer.ftp.client;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.RequiredServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.scp.client.DefaultScpClientCreator;
import org.apache.sshd.scp.client.ScpClient;
import org.springframework.stereotype.Service;

import it.eng.datatransfer.ftp.configuration.FTPConfiguration;
import it.eng.tools.configuration.GlobalSSLConfiguration;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FTPClient {
	
	private final GlobalSSLConfiguration sslConfiguration;
	private final FTPConfiguration ftpConfiguration;
	
	public FTPClient(GlobalSSLConfiguration sslConfiguration, FTPConfiguration ftpConfiguration) {
		super();
		this.sslConfiguration = sslConfiguration;
		this.ftpConfiguration = ftpConfiguration;
	}


	public boolean downloadArtifact(String artifact, String serverHost, int serverPort) {
		SshClient client = startClient();
		ClientSession clientSession = startClientSession(client, serverHost, serverPort);

		boolean downloadSuccess = false;
		Instant start = Instant.now(); // Start time measurement
		try {
			log.info("Downloading file " + artifact);
			ScpClient scpClient = DefaultScpClientCreator.INSTANCE.createScpClient(clientSession);
			scpClient.download(artifact, 
					Paths.get(ftpConfiguration.getDownloadFolder()), 
					ScpClient.Option.PreserveAttributes,
					ScpClient.Option.TargetIsDirectory);
			log.info("File " + artifact + " downloaded");
			Instant finish = Instant.now(); // End time measurement
			long timeElapsed = Duration.between(start, finish).toMillis(); // Calculate duration in milliseconds
			log.info("Time taken: " + timeElapsed + " ms");
			scpClient.getSession().disconnect(200, "Artifact downloaded");
			downloadSuccess = true;
		} catch (IOException e) {
			log.error("Error while downloading file " + artifact + " : " + e.getMessage());
		} finally {
			clientSession.close(false);
			client.stop();
			client.close(false);
		}
		return downloadSuccess;
	}
	
	private SshClient startClient() {
		log.info("Starting SFTP client...");
		SshClient client = SshClient.setUpDefaultClient();
		client.addPublicKeyIdentity(sslConfiguration.getKeyPair());
		client.addPasswordIdentity(
				sslConfiguration.getSslBundles().getBundle("connector").getStores().getKeyStorePassword());
		client.setServerKeyVerifier(new RequiredServerKeyVerifier(sslConfiguration.getPublicKey()));
		client.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(sslConfiguration.getKeyPair()));
		client.start();
		log.info("SFTP client started");
		return client;
	}


	private ClientSession startClientSession(SshClient client, String serverHost, int serverPort) {
		try {
			log.info("Connecting to " + serverHost + ":" + serverPort);
			ClientSession clientSession = client
					.connect(ftpConfiguration.getClientUsername(), serverHost, serverPort)
					.verify(ftpConfiguration.getDefaultTimeoutSeconds(), TimeUnit.SECONDS).getSession();
			clientSession.addPublicKeyIdentity(sslConfiguration.getKeyPair());
			clientSession.auth().verify(ftpConfiguration.getDefaultTimeoutSeconds(), TimeUnit.SECONDS);
			log.info("Connection established");
			return clientSession;
		} catch (IOException e) {
			log.error("Connection could not be established: " + e.getMessage());
		}
		return null;
	}
}
