package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.rest.api.CatalogAPIController;
import it.eng.catalog.rest.api.DataServiceAPIController;
import it.eng.catalog.rest.api.DatasetAPIController;
import it.eng.catalog.rest.api.DistributionAPIController;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.rest.api.DSpaceVersionController;
import it.eng.datatransfer.rest.api.DataTransferAPIController;
import it.eng.negotiation.rest.api.AgreementAPIController;
import it.eng.negotiation.rest.api.ContractNegotiationAPIController;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
// Springdoc creates a separate context, so use a random port to avoid colliding with the shared 8080 server.
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "server.port=0")
class OpenApiDocsDriftIT extends BaseIntegrationTest {

    private static final Set<Class<?>> FULLY_DOCUMENTED_CONTROLLERS = Set.of(
            CatalogAPIController.class,
            DatasetAPIController.class,
            DistributionAPIController.class,
            DataServiceAPIController.class,
            ContractNegotiationAPIController.class,
            AgreementAPIController.class,
            DataTransferAPIController.class);

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
    @DisplayName("Fully documented management API controllers have summaries and descriptions")
    void fullyDocumentedControllersHaveSummariesAndDescriptions() {
        Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();
        Map<String, OpenApiOperationDocs> operations = docsLoader.load().operations();
        List<String> undocumentedOperations = mappings.values().stream()
                .filter(handlerMethod -> FULLY_DOCUMENTED_CONTROLLERS.contains(handlerMethod.getBeanType()))
                .filter(handlerMethod -> !hasSummaryAndDescription(handlerMethod, operations))
                .map(handlerMethod -> handlerMethod.getBeanType().getSimpleName()
                        + "." + handlerMethod.getMethod().getName())
                .toList();

        assertTrue(undocumentedOperations.isEmpty(),
                () -> "Management API operations are missing summaries or descriptions: "
                        + String.join("; ", undocumentedOperations));
    }

    @Test
    @DisplayName("Negotiation and transfer GET mappings do not require a JSON content type")
    void negotiationAndTransferGetMappingsDoNotRequireJsonContentType() {
        Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();
        List<String> constrainedGetMappings = mappings.entrySet().stream()
                .filter(entry -> FULLY_DOCUMENTED_CONTROLLERS.contains(entry.getValue().getBeanType()))
                .filter(entry -> entry.getValue().getMethod().isAnnotationPresent(GetMapping.class))
                .filter(entry -> !entry.getKey().getConsumesCondition().isEmpty())
                .map(entry -> entry.getValue().getBeanType().getSimpleName()
                        + "." + entry.getValue().getMethod().getName())
                .toList();

        assertTrue(constrainedGetMappings.isEmpty(),
                () -> "GET mappings must not require Content-Type: " + String.join("; ", constrainedGetMappings));
    }

    @Test
    @DisplayName("Catalog create documents a structured JSON body and its request example")
    void catalogCreateDocumentsStructuredJsonRequestExample() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs/management-api")
                        .with(user("swagger-test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = jsonMapper.readTree(document);
        JsonNode mediaType = openApi.path("paths").path("/api/v1/catalogs").path("post")
                .path("requestBody").path("content").path("application/json");

        assertFalse("string".equals(mediaType.path("schema").path("type").asText()),
                "The catalog request body should not be documented as a string");
        assertEquals("#/components/examples/catalog.plain",
                mediaType.path("examples").path("Catalog").path("$ref").asText());
        assertTrue(openApi.path("components").path("examples")
                .path("catalog.plain").path("value").isObject());
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
