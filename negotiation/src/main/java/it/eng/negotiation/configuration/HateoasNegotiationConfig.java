package it.eng.negotiation.configuration;

import it.eng.negotiation.model.ContractNegotiation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.data.web.PagedResourcesAssembler;

@Configuration
public class HateoasNegotiationConfig {

    @Bean
    public PagedResourcesAssembler<ContractNegotiation> contractNegotiationPagedResourcesAssembler(
            HateoasPageableHandlerMethodArgumentResolver resolver) {
        return new PagedResourcesAssembler<>(resolver, null);
    }
}
