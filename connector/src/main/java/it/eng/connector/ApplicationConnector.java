package it.eng.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan({"it.eng.connector", "it.eng.catalog", "it.eng.negotiation", "it.eng.tools", "it.eng.datatransfer"})
@EnableMongoRepositories(basePackages = {"it.eng.connector.repository", "it.eng.catalog.repository", "it.eng.negotiation.repository"})
public class ApplicationConnector {

    
	public static void main(String[] args) {
		System.setProperty("server.error.include-stacktrace", "never");
		SpringApplication.run(ApplicationConnector.class, args);
	}
}


