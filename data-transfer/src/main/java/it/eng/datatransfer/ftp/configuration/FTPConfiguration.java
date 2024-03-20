package it.eng.datatransfer.ftp.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.NoArgsConstructor;

@Configuration
@ConfigurationProperties(prefix = "application.ftp")
@Data
@NoArgsConstructor
public class FTPConfiguration {
	private String host;
	private int serverPort;
	private String serverUsername;
	private String serverFolder;
	
	private String clientUsername;
	private String downloadFolder;
	long defaultTimeoutSeconds;
}
