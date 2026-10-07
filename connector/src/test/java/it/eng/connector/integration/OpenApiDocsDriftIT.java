package it.eng.connector.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import it.eng.connector.configuration.OpenApiDocsDriftValidator;
import it.eng.connector.configuration.OpenApiDocsLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class OpenApiDocsDriftIT extends BaseIntegrationTest {

    @Autowired
    private OpenApiDocsLoader docsLoader;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Every loaded OpenAPI operation reference maps to a Spring MVC handler")
    void loadedOperationReferencesMapToSpringMvcHandlers() {
        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(docsLoader, handlerMapping));
    }
}
