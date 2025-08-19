package it.eng.tools.configuration;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.model.ApplicationProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.data.web.PagedResourcesAssembler;

@Configuration
public class HateoasConfig {

    @Bean
    public HateoasPageableHandlerMethodArgumentResolver hateoasPageableHandlerMethodArgumentResolver() {
        return new HateoasPageableHandlerMethodArgumentResolver();
    }

    @Bean
    public PagedResourcesAssembler<AuditEvent> auditEventPagedResourcesAssembler(
            HateoasPageableHandlerMethodArgumentResolver resolver) {
        return new PagedResourcesAssembler<>(resolver, null);
    }

    @Bean
    public PagedResourcesAssembler<ApplicationProperty> applicationPropertyPagedResourcesAssembler(
            HateoasPageableHandlerMethodArgumentResolver resolver) {
        return new PagedResourcesAssembler<>(resolver, null);
    }
}
