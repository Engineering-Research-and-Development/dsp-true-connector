package it.eng.connector.configuration;

import it.eng.connector.model.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.data.web.PagedResourcesAssembler;

@Configuration
public class HateoasConnectorConfig {

    @Bean
    public PagedResourcesAssembler<User> userPagedResourcesAssembler(
            HateoasPageableHandlerMethodArgumentResolver resolver) {
        return new PagedResourcesAssembler<>(resolver, null);
    }
}
