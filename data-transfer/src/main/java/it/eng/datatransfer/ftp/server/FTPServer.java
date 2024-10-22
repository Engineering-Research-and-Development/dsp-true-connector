package it.eng.datatransfer.ftp.server;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collections;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import it.eng.datatransfer.event.StartFTPServerEvent;
import it.eng.datatransfer.event.StopFTPServerEvent;
import it.eng.datatransfer.ftp.configuration.FTPConfiguration;
import it.eng.tools.configuration.GlobalSSLConfiguration;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FTPServer {
	
	private final GlobalSSLConfiguration sslConfiguration;
	private final FTPConfiguration ftpConfiguration;
	private final PublickeyAuthenticator authenticator;
	
	SshServer sshd;
	
	public FTPServer(GlobalSSLConfiguration sslConfiguration, @Qualifier(value = "FTPAuthenticator") PublickeyAuthenticator authenticator, FTPConfiguration ftpConfiguration) throws IOException, URISyntaxException {
		super();
		this.sslConfiguration = sslConfiguration;
		this.ftpConfiguration = ftpConfiguration;
		this.authenticator = authenticator;
//		start();
	}

	@EventListener
	public void start(StartFTPServerEvent startFtpEvent) throws IOException, URISyntaxException {
		log.info("Starting SFTP server...");
		sshd = SshServer.setUpDefaultServer();
		sshd.setPort(ftpConfiguration.getServerPort());

		sshd.setKeyPairProvider(new MappedKeyPairProvider(sslConfiguration.getKeyPair()));
		
		sshd.setPublickeyAuthenticator(authenticator);
		sshd.setKeyboardInteractiveAuthenticator(KeyboardInteractiveAuthenticator.NONE);
		
		SftpSubsystemFactory factory = new SftpSubsystemFactory.Builder().build();
		sshd.setSubsystemFactories(Collections.singletonList(factory));
		sshd.setCommandFactory(new ScpCommandFactory());

		//"/home/nobody/ftp"
		sshd.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get(ftpConfiguration.getServerFolder())));
		
		sshd.start();
		log.info("SFTP server started");
	}
	
	@EventListener
	public void shutdown(StopFTPServerEvent stopFtpEvent) throws IOException {
		shutdownFtp();
	}

	@PreDestroy
	public void shutdownFtp() throws IOException {
		if(sshd != null && sshd.isStarted()) {
			sshd.stop();
			log.info("Shutting down SFTP server...");
		} else {
			log.info("FTP not started - not stopping it");
		}
	}
}
