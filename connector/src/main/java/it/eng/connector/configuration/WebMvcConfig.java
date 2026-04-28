package it.eng.connector.configuration;

import it.eng.connector.interceptor.TenantContextClearingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration that registers application-wide interceptors.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantContextClearingInterceptor tenantContextClearingInterceptor;

    /**
     * Constructs the configuration with its interceptor dependency.
     *
     * @param tenantContextClearingInterceptor the interceptor that clears the tenant context
     */
    public WebMvcConfig(TenantContextClearingInterceptor tenantContextClearingInterceptor) {
        this.tenantContextClearingInterceptor = tenantContextClearingInterceptor;
    }

    /**
     * Registers the tenant context clearing interceptor for all requests.
     *
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantContextClearingInterceptor);
    }
}
