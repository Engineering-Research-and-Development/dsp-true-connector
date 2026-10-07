package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.rest.api.DSpaceVersionController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @DisplayName("Empty loaded OpenAPI documentation passes validation")
    void emptyLoadedDocumentationPassesValidation() {
        OpenApiDocs documentation = docsLoader.load();

        assertTrue(documentation.operations().isEmpty());
        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(
                documentation, handlerMapping.getHandlerMethods()));
    }

    @Test
    @DisplayName("A synthetic OpenAPI operation maps to a registered Spring MVC handler")
    void syntheticOperationMapsToSpringMvcHandler() {
        OpenApiDocs documentation = new OpenApiDocs(
                List.of(),
                Map.of("getVersion", new OpenApiOperationDocs(
                        "getVersion", Optional.empty(), Map.of())),
                Map.of(),
                Map.of());

        assertTrue(handlerMapping.getHandlerMethods().values().stream()
                .anyMatch(handlerMethod -> handlerMethod.getBeanType().equals(DSpaceVersionController.class)
                        && handlerMethod.getMethod().getName().equals("getVersion")));
        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(
                documentation, handlerMapping.getHandlerMethods()));
    }
}
