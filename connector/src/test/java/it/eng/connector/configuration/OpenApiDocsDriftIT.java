package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.catalog.rest.api.CatalogAPIController;
import it.eng.catalog.rest.api.DataServiceAPIController;
import it.eng.catalog.rest.api.DatasetAPIController;
import it.eng.catalog.rest.api.DistributionAPIController;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.rest.api.DSpaceVersionController;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class OpenApiDocsDriftIT extends BaseIntegrationTest {

    private static final Set<Class<?>> FULLY_DOCUMENTED_CONTROLLERS = Set.of(
            CatalogAPIController.class,
            DatasetAPIController.class,
            DistributionAPIController.class,
            DataServiceAPIController.class);

    @Autowired
    private OpenApiDocsLoader docsLoader;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Loaded OpenAPI documentation maps to registered Spring MVC handlers")
    void loadedDocumentationMapsToRegisteredHandlers() {
        OpenApiDocs documentation = docsLoader.load();

        assertFalse(documentation.operations().isEmpty());
        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(
                documentation, handlerMapping.getHandlerMethods()));
    }

    @Test
    @DisplayName("Fully documented catalog controllers have summaries and descriptions")
    void fullyDocumentedCatalogControllersHaveSummariesAndDescriptions() {
        Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();
        Map<String, OpenApiOperationDocs> operations = docsLoader.load().operations();
        List<String> undocumentedOperations = mappings.values().stream()
                .filter(handlerMethod -> FULLY_DOCUMENTED_CONTROLLERS.contains(handlerMethod.getBeanType()))
                .filter(handlerMethod -> !hasSummaryAndDescription(handlerMethod, operations))
                .map(handlerMethod -> handlerMethod.getBeanType().getSimpleName()
                        + "." + handlerMethod.getMethod().getName())
                .toList();

        assertTrue(undocumentedOperations.isEmpty(),
                () -> "Catalog operations are missing summaries or descriptions: "
                        + String.join("; ", undocumentedOperations));
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

    private static boolean hasSummaryAndDescription(
            final HandlerMethod handlerMethod,
            final Map<String, OpenApiOperationDocs> operations) {
        OpenApiOperationDocs operation = operations.get(handlerMethod.getMethod().getName());
        if (operation == null || !operation.methodName().equals(handlerMethod.getMethod().getName())) {
            return false;
        }
        if (operation.methodParameters().isPresent()
                && !operation.methodParameters().get().equals(Arrays.stream(
                        handlerMethod.getMethod().getParameterTypes())
                        .map(Class::getTypeName)
                        .toList())) {
            return false;
        }
        Object summary = operation.properties().get("summary");
        Object description = operation.properties().get("description");
        return summary instanceof String summaryText && !summaryText.isBlank()
                && description instanceof String descriptionText && !descriptionText.isBlank();
    }
}
