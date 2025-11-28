package it.eng.connector.configuration;

import it.eng.datatransfer.filter.EndpointAvailableFilter;
import it.eng.datatransfer.service.AgreementService;
import it.eng.datatransfer.service.TransferProcessStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterRegistrationConfig {

    @Autowired
    public AgreementService agreementService;

    @Autowired
    private TransferProcessStrategy dataTransferService;

    @Bean
    FilterRegistrationBean<EndpointAvailableFilter> endpointAvailableFilter() {
        FilterRegistrationBean<EndpointAvailableFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new EndpointAvailableFilter(agreementService, dataTransferService));
        registrationBean.addUrlPatterns("/artifacts/*");
        return registrationBean;
    }
}
