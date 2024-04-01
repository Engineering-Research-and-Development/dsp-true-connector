//package it.eng.connector.configuration;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.domain.AuditorAware;
//import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
//import org.springframework.transaction.annotation.EnableTransactionManagement;
//
//import it.eng.catalog.entity.AuditorAwareImpl;
//
//@Configuration
//@EnableTransactionManagement
//@EnableJpaAuditing
//public class PersistenceConfigConnector {
//
//	@Bean
//	AuditorAware<String> auditorProvider() {
//		return new AuditorAwareImpl();
//	}
//
//
////	@Bean
////	public Jackson2RepositoryPopulatorFactoryBean getRespositoryPopulator() {
////	    Jackson2RepositoryPopulatorFactoryBean factory = new Jackson2RepositoryPopulatorFactoryBean();
////	    factory.setResources(new Resource[]
////	    		{new ClassPathResource("catalog.json"),
////	    				new ClassPathResource("dataservice.json"),
////	    	    		new ClassPathResource("dataset.json"),
////	    	    		new ClassPathResource("users.json"),
////	    	    		new ClassPathResource("contract_offer.json")
//////	    	    		new ClassPathResource("contract_offer2.json")
////	    	    		});
//////	    		{new ClassPathResource("users.json")});
////	    return factory;
////	}
//}
