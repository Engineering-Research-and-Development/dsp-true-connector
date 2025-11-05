package it.eng.dcp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {"it.eng.dcp.repository"})
public class DCPMongoConfig {

}
