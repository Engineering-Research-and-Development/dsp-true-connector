package it.eng.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan({"it.eng.connector", "it.eng.catalog", "it.eng.negotiation", "it.eng.tools", "it.eng.datatransfer"})
@EnableJpaRepositories(basePackages = {"it.eng.catalog", "it.eng.connector", "it.eng.negotiation.*"})
@EntityScan({"it.eng.catalog.*", "it.eng.connector.*", "it.eng.negotiation.*"})
public class ApplicationConnector {

    
	public static void main(String[] args) throws Exception {
		System.setProperty("server.error.include-stacktrace", "never");
		SpringApplication.run(ApplicationConnector.class, args);
	}
}


