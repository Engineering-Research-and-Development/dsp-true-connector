package it.eng.datatransfer.configuration;

import it.eng.datatransfer.model.TransferProcess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.data.web.PagedResourcesAssembler;

@Configuration
public class HateoasTransferProcessConfig {

    @Bean
    public PagedResourcesAssembler<TransferProcess> transferProcessPagedResourcesAssembler(
            HateoasPageableHandlerMethodArgumentResolver resolver) {
        return new PagedResourcesAssembler<>(resolver, null);
    }
}
