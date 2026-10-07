package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import it.eng.tools.model.TenantCreateRequest;
import it.eng.tools.rest.api.TenantAPIController;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class OpenApiDocsCustomizerTest {

    private static final String CONTROLLER_NAME = "TenantAPIController";

    @Test
    @DisplayName("Applies operation documentation and controller tag metadata")
    void appliesOperationDocumentationAndControllerTagMetadata() throws NoSuchMethodException {
        HandlerMethod handlerMethod = handlerMethod("createTenant", TenantCreateRequest.class);
        OpenApiDocs docs = docs(
                List.of(Map.of(CONTROLLER_NAME, Map.of(
                        "name", CONTROLLER_NAME,
                        "description", "Tenant management operations"))),
                Map.of("createTenant", operationDocs("createTenant", Map.of(
                        "summary", "Create a tenant",
                        "description", "Creates a tenant for this connector.",
                        "responses", Map.of("201", Map.of("description", "Tenant created"))))),
                Map.of(),
                Map.of());
        OpenApiDocsCustomizer customizer = customizer(docs, Map.of());
        Operation operation = new Operation();

        Operation customized = customizer.customize(operation, handlerMethod);

        assertSame(operation, customized);
        assertEquals("Create a tenant", operation.getSummary());
        assertEquals("Creates a tenant for this connector.", operation.getDescription());
        assertEquals(List.of(CONTROLLER_NAME), operation.getTags());
        assertEquals("Tenant created", operation.getResponses().get("201").getDescription());

        OpenAPI openAPI = new OpenAPI().components(new Components());
        customizer.customise(openAPI);

        assertEquals(CONTROLLER_NAME, openAPI.getTags().get(0).getName());
        assertEquals("Tenant management operations", openAPI.getTags().get(0).getDescription());
    }

    @Test
    @DisplayName("Leaves an operation unchanged when no documentation is loaded")
    void leavesOperationUnchangedWhenDocumentationIsAbsent() throws NoSuchMethodException {
        OpenApiDocsCustomizer customizer = customizer(docs(List.of(), Map.of(), Map.of(), Map.of()), Map.of());
        Operation operation = new Operation().summary("Existing summary");

        Operation customized = customizer.customize(
                operation, handlerMethod("createTenant", TenantCreateRequest.class));

        assertSame(operation, customized);
        assertEquals("Existing summary", operation.getSummary());
        assertNull(operation.getTags());
    }

    @Test
    @DisplayName("Registers an available request example once and references it from the request body")
    void registersAndReferencesAvailableRequestExample() throws NoSuchMethodException {
        HandlerMethod handlerMethod = handlerMethod("createTenant", TenantCreateRequest.class);
        OpenApiDocs docs = docs(List.of(), Map.of("createTenant", operationDocs("createTenant", Map.of(
                        "requestExamples", List.of("tenant-create")))),
                Map.of(), Map.of(
                        "tenant-create.plain", "{\"name\":\"Acme\"}",
                        "tenant-create.protocol", "{\"@type\":\"Tenant\"}"));
        OpenApiDocsCustomizer customizer = customizer(docs, Map.of("/api/v1/tenants", handlerMethod));
        OpenAPI openAPI = new OpenAPI().components(new Components());
        Operation operation = operationWithRequestBody();

        customizer.customize(operation, handlerMethod);
        customizer.customise(openAPI);

        assertEquals(1, openAPI.getComponents().getExamples().size());
        Example registeredExample = openAPI.getComponents().getExamples().get("tenant-create.plain");
        assertEquals("{\"name\":\"Acme\"}", registeredExample.getValue().toString());
        assertEquals("#/components/examples/tenant-create.plain",
                operation.getRequestBody().getContent().get("application/json")
                        .getExamples().get("tenant-create").get$ref());
    }

    @Test
    @DisplayName("Does not register or reference an example when its resource is missing")
    void ignoresMissingRequestExampleResource() throws NoSuchMethodException {
        HandlerMethod handlerMethod = handlerMethod("createTenant", TenantCreateRequest.class);
        OpenApiDocs docs = docs(List.of(), Map.of("createTenant", operationDocs("createTenant", Map.of(
                        "requestExamples", List.of("missing-example")))),
                Map.of(), Map.of());
        OpenApiDocsCustomizer customizer = customizer(docs, Map.of("/api/v1/tenants", handlerMethod));
        OpenAPI openAPI = new OpenAPI().components(new Components());
        Operation operation = operationWithRequestBody();

        customizer.customize(operation, handlerMethod);
        customizer.customise(openAPI);

        assertNull(openAPI.getComponents().getExamples());
        Map<String, Example> requestExamples =
                operation.getRequestBody().getContent().get("application/json").getExamples();
        assertTrue(requestExamples == null || requestExamples.isEmpty());
    }

    @Test
    @DisplayName("Uses the protocol example variant for DSP mappings")
    void usesProtocolExampleVariantForDspMappings() throws NoSuchMethodException {
        HandlerMethod handlerMethod = handlerMethod("createTenant", TenantCreateRequest.class);
        OpenApiDocs docs = docs(List.of(), Map.of("createTenant", operationDocs("createTenant", Map.of(
                        "requestExamples", List.of("tenant-create")))),
                Map.of(), Map.of(
                        "tenant-create.plain", "{\"name\":\"Plain\"}",
                        "tenant-create.protocol", "{\"@type\":\"Tenant\"}"));
        OpenApiDocsCustomizer customizer = customizer(
                docs, Map.of("/{tenantId}/catalog", handlerMethod));
        OpenAPI openAPI = new OpenAPI().components(new Components());
        Operation operation = operationWithRequestBody();

        customizer.customize(operation, handlerMethod);
        customizer.customise(openAPI);

        assertEquals(1, openAPI.getComponents().getExamples().size());
        assertEquals("{\"@type\":\"Tenant\"}",
                openAPI.getComponents().getExamples().get("tenant-create.protocol").getValue().toString());
        assertEquals("#/components/examples/tenant-create.protocol",
                operation.getRequestBody().getContent().get("application/json")
                        .getExamples().get("tenant-create").get$ref());
    }

    @Test
    @DisplayName("Updates only fields declared in the matching schema documentation")
    void appliesSchemaFieldDescriptionsOnlyToMatchingProperties() {
        Schema<?> id = new Schema<>().type("string");
        Schema<?> name = new Schema<>().type("string");
        Schema<?> other = new Schema<>().type("string");
        Schema<?> tenant = new Schema<>().addProperties("id", id).addProperties("name", name);
        Schema<?> unrelated = new Schema<>().addProperties("name", other);
        OpenAPI openAPI = new OpenAPI().components(new Components().schemas(Map.of(
                "Tenant", tenant,
                "Unrelated", unrelated)));
        OpenApiDocs docs = docs(List.of(), Map.of(), Map.of("Tenant", Map.of(
                "id", "The stable tenant identifier.",
                "unknown", "No matching property.")), Map.of());

        customizer(docs, Map.of()).customise(openAPI);

        assertEquals("The stable tenant identifier.", id.getDescription());
        assertNull(name.getDescription());
        assertNull(other.getDescription());
    }

    @Test
    @DisplayName("Applies overloaded method documentation only to the selected paged signature")
    void selectsOnlyPagedGetAllTenantsOverload() throws NoSuchMethodException {
        Method pagedMethod = TenantAPIController.class.getDeclaredMethod(
                "getAllTenants", HttpServletRequest.class, int.class, int.class, String[].class);
        Method listMethod = TenantAPIController.class.getDeclaredMethod("getAllTenants");
        TenantAPIController controller = mock(TenantAPIController.class);
        HandlerMethod pagedHandler = new HandlerMethod(controller, pagedMethod);
        HandlerMethod listHandler = new HandlerMethod(controller, listMethod);
        OpenApiDocs docs = docs(List.of(), Map.of("getAllTenants", new OpenApiOperationDocs(
                "getAllTenants",
                Optional.of(List.of(
                        "jakarta.servlet.http.HttpServletRequest",
                        "int",
                        "int",
                        "java.lang.String[]")),
                Map.of("summary", "List tenants with pagination."))),
                Map.of(), Map.of());
        OpenApiDocsCustomizer customizer = customizer(docs, Map.of());
        Operation pagedOperation = new Operation();
        Operation listOperation = new Operation();

        customizer.customize(pagedOperation, pagedHandler);
        customizer.customize(listOperation, listHandler);

        assertEquals("List tenants with pagination.", pagedOperation.getSummary());
        assertNull(listOperation.getSummary());
        assertNull(listOperation.getTags());
    }

    private static OpenApiDocsCustomizer customizer(OpenApiDocs docs, Map<String, HandlerMethod> mappings) {
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = mappings.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> RequestMappingInfo.paths(entry.getKey()).build(),
                        Map.Entry::getValue));
        when(handlerMapping.getHandlerMethods()).thenReturn(handlerMethods);
        return new OpenApiDocsCustomizer(docs, handlerMapping);
    }

    private static HandlerMethod handlerMethod(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = TenantAPIController.class.getDeclaredMethod(methodName, parameterTypes);
        return new HandlerMethod(mock(TenantAPIController.class), method);
    }

    private static Operation operationWithRequestBody() {
        return new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType())));
    }

    private static OpenApiOperationDocs operationDocs(String methodName, Map<String, Object> properties) {
        return new OpenApiOperationDocs(methodName, Optional.empty(), properties);
    }

    private static OpenApiDocs docs(
            List<Map<String, Object>> tags,
            Map<String, OpenApiOperationDocs> operations,
            Map<String, Object> schemas,
            Map<String, String> examples) {
        return new OpenApiDocs(tags, operations, schemas, examples);
    }
}
