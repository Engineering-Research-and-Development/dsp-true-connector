package it.eng.connector.configuration;

import it.eng.connector.filter.ApiTenantContextFilter;
import it.eng.datatransfer.filter.EndpointAvailableFilter;
import it.eng.datatransfer.service.AgreementService;
import it.eng.datatransfer.service.TransferProcessStrategy;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterRegistrationConfig {

    public final AgreementService agreementService;
    private final TransferProcessStrategy dataTransferService;

    public FilterRegistrationConfig(AgreementService agreementService, TransferProcessStrategy dataTransferService) {
        this.agreementService = agreementService;
        this.dataTransferService = dataTransferService;
    }

    @Bean
    FilterRegistrationBean<EndpointAvailableFilter> endpointAvailableFilter() {
        FilterRegistrationBean<EndpointAvailableFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new EndpointAvailableFilter(agreementService, dataTransferService));
        registrationBean.addUrlPatterns("/artifacts/*");
        return registrationBean;
    }

    /**
     * Prevents Spring Boot from auto-registering {@link ApiTenantContextFilter} as a global servlet filter.
     * The filter is added only to the admin security filter chain via {@code ConnectorSecurityConfig}.
     *
     * @param filter the filter bean to disable for global registration
     * @return a disabled {@link FilterRegistrationBean}
     */
    @Bean
    FilterRegistrationBean<ApiTenantContextFilter> apiTenantContextFilterRegistration(
            ApiTenantContextFilter filter) {
        FilterRegistrationBean<ApiTenantContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

