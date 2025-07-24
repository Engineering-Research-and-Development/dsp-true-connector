package it.eng.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"it.eng.connector", "it.eng.catalog", "it.eng.negotiation", "it.eng.tools", "it.eng.datatransfer"})
public class ApplicationConnector {


    public static void main(String[] args) {
        System.setProperty("server.error.include-stacktrace", "never");
        SpringApplication.run(ApplicationConnector.class, args);
    }

    // passing the security context to the async tasks
//    @PostConstruct
//    void setGlobalSecurityContext() {
//        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
//    }
}


