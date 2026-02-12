package it.eng.dcp.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Web MVC configuration for DCP module.
 *
 * <p>Ensures consistent path pattern parsing for both static and dynamically registered endpoints.
 * This configuration is required when using {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
 * to dynamically register endpoints at runtime, as done by {@link it.eng.dcp.common.rest.GenericDidDocumentController}.
 *
 * <p>Spring Boot 3.x uses {@link PathPatternParser} by default. This configuration explicitly
 * ensures it remains enabled for all endpoints.
 *
 * @see it.eng.dcp.common.rest.GenericDidDocumentController
 * @see PathPatternParser
 */
@Configuration
public class DcpWebMvcConfiguration implements WebMvcConfigurer {

    /**
     * Configure path matching to use PathPatternParser.
     *
     * <p>Spring Boot 3.x uses PathPatternParser by default, but we explicitly configure it
     * to ensure dynamically registered endpoints use the same pattern matching strategy.
     *
     * @param configurer path match configurer
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Explicitly set PathPatternParser (this is the default in Spring Boot 3.x)
        configurer.setPatternParser(new PathPatternParser());
    }
}



