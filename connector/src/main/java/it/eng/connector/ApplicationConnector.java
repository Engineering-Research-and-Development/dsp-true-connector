package it.eng.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan({"it.eng.connector", "it.eng.catalog", "it.eng.negotiation", "it.eng.tools", "it.eng.datatransfer"})
@EnableScheduling
public class ApplicationConnector {


    public static void main(String[] args) {
        System.setProperty("server.error.include-stacktrace", "never");
        SpringApplication.run(ApplicationConnector.class, args);
    }
}


