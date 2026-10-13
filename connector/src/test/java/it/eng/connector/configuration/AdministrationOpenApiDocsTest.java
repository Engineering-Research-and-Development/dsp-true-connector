package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.connector.rest.api.AuthController;
import it.eng.connector.rest.api.DashboardMetricsController;
import it.eng.connector.rest.api.DSpaceVersionController;
import it.eng.connector.rest.api.KeycloakUserApiController;
import it.eng.connector.rest.api.UserAPIController;
import it.eng.tools.rest.api.ApplicationPropertiesAPIController;
import it.eng.tools.rest.api.AuditEventController;
import it.eng.tools.rest.api.TenantAPIController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.RequestMapping;

class AdministrationOpenApiDocsTest {

    private static final List<Class<?>> FULLY_DOCUMENTED_CONTROLLERS = List.of(
            TenantAPIController.class,
            UserAPIController.class);

    private static final List<Class<?>> BRIEF_DOCUMENTED_CONTROLLERS = List.of(
            AuthController.class,
            KeycloakUserApiController.class,
            AuditEventController.class,
            ApplicationPropertiesAPIController.class,
            DashboardMetricsController.class,
            DSpaceVersionController.class);

    @Test
    @DisplayName("Tenant and user operations have OpenAPI summaries and descriptions")
    void fullyDocumentedOperationsHaveSummariesAndDescriptions() {
        OpenApiDocs documentation = loadDocumentation();
        List<String> undocumentedOperations = operationsFor(FULLY_DOCUMENTED_CONTROLLERS).stream()
                .map(operation -> missingDocumentation(
                        operation.controller(), operation.method(), documentation.operations(), true))
                .filter(message -> !message.isEmpty())
                .toList();

        assertTrue(undocumentedOperations.isEmpty(),
                () -> "Administration operations are missing OpenAPI documentation: "
                        + String.join("; ", undocumentedOperations));
    }

    @Test
    @DisplayName("Supporting administration operations have OpenAPI summaries")
    void briefOperationsHaveSummaries() {
        OpenApiDocs documentation = loadDocumentation();
        List<String> undocumentedOperations = operationsFor(BRIEF_DOCUMENTED_CONTROLLERS).stream()
                .map(operation -> missingDocumentation(
                        operation.controller(), operation.method(), documentation.operations(), false))
                .filter(message -> !message.isEmpty())
                .toList();

        assertTrue(undocumentedOperations.isEmpty(),
                () -> "Supporting operations are missing OpenAPI summaries: "
                        + String.join("; ", undocumentedOperations));
    }

    @Test
    @DisplayName("Administration tags and generated examples are present")
    void tagsAndGeneratedExamplesArePresent() {
        OpenApiDocs documentation = loadDocumentation();
        List<String> missingTags = Stream.concat(
                        FULLY_DOCUMENTED_CONTROLLERS.stream(),
                        BRIEF_DOCUMENTED_CONTROLLERS.stream())
                .map(Class::getSimpleName)
                .filter(controllerName -> documentation.tags().stream()
                        .noneMatch(tag -> tag.containsKey(controllerName)))
                .toList();

        assertTrue(missingTags.isEmpty(),
                () -> "Administration controllers are missing OpenAPI tags: "
                        + String.join("; ", missingTags));
        assertTrue(documentation.examples().keySet().containsAll(List.of(
                        "application-properties.plain",
                        "login-admin.plain",
                        "login-connector.plain")),
                "Generated administration examples should match the OpenAPI example references");
        Object loginExamples = documentation.operations().get("login").properties().get("requestExamples");
        assertTrue(loginExamples instanceof Map<?, ?> examples
                        && examples.values().containsAll(List.of("login-admin", "login-connector")),
                "Login should reference both seeded-account examples");
    }

    @Test
    @DisplayName("Tenant and user documentation covers isolation and access boundaries")
    void securityAndTenantBehaviorAreDocumented() {
        OpenApiDocs documentation = loadDocumentation();
        Map<String, OpenApiOperationDocs> operations = documentation.operations();
        String tenantDocumentation = operationsFor(FULLY_DOCUMENTED_CONTROLLERS).stream()
                .filter(operation -> operation.controller().equals(TenantAPIController.class))
                .map(operation -> operationDocumentation(operations, operation.method()))
                .reduce(controllerTagDescription(documentation, TenantAPIController.class.getSimpleName()),
                        (left, right) -> left + " " + right);
        String userDocumentation = operationsFor(FULLY_DOCUMENTED_CONTROLLERS).stream()
                .filter(operation -> operation.controller().equals(UserAPIController.class))
                .map(operation -> operationDocumentation(operations, operation.method()))
                .reduce(controllerTagDescription(documentation, UserAPIController.class.getSimpleName()),
                        (left, right) -> left + " " + right);

        assertTrue(tenantDocumentation.contains("ROLE_SUPER_ADMIN"));
        assertTrue(tenantDocumentation.contains("X-Tenant-Id"));
        assertTrue(tenantDocumentation.toLowerCase().contains("bucket"));
        assertTrue(tenantDocumentation.toLowerCase().contains("disabled"));
        assertTrue(userDocumentation.contains("ROLE_SUPER_ADMIN"));
        assertTrue(userDocumentation.contains("ROLE_ADMIN"));
        assertTrue(userDocumentation.contains("ROLE_CONNECTOR"));
        assertTrue(userDocumentation.contains("INTERNAL"));
        assertTrue(userDocumentation.contains("KEYCLOAK"));
        assertTrue(userDocumentation.toLowerCase().contains("password"));
    }

    private static OpenApiDocs loadDocumentation() {
        return new OpenApiDocsLoader(new PathMatchingResourcePatternResolver()).load();
    }

    private static List<ControllerOperation> operationsFor(final List<Class<?>> controllers) {
        return controllers.stream()
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods())
                        .filter(AdministrationOpenApiDocsTest::isRequestMapping)
                        .map(method -> new ControllerOperation(controller, method)))
                .toList();
    }

    private static boolean isRequestMapping(final Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    private static String missingDocumentation(
            final Class<?> controller,
            final Method method,
            final Map<String, OpenApiOperationDocs> operations,
            final boolean requireDescription) {
        OpenApiOperationDocs operation = operations.get(method.getName());
        if (operation == null || !operation.methodName().equals(method.getName())
                || !methodParametersMatch(operation, method)) {
            return controller.getSimpleName() + "." + method.getName() + " has no matching OpenAPI operation";
        }
        if (isBlank(operation.properties().get("summary"))) {
            return controller.getSimpleName() + "." + method.getName() + " is missing an OpenAPI summary";
        }
        if (requireDescription && isBlank(operation.properties().get("description"))) {
            return controller.getSimpleName() + "." + method.getName() + " is missing an OpenAPI description";
        }
        return "";
    }

    private static boolean methodParametersMatch(final OpenApiOperationDocs operation, final Method method) {
        return operation.methodParameters()
                .map(expected -> expected.equals(Arrays.stream(method.getParameterTypes())
                        .map(Class::getTypeName)
                        .toList()))
                .orElse(true);
    }

    private static String operationDocumentation(
            final Map<String, OpenApiOperationDocs> operations,
            final Method method) {
        OpenApiOperationDocs operation = operations.get(method.getName());
        if (operation == null) {
            return "";
        }
        return String.valueOf(operation.properties().get("summary")) + " "
                + String.valueOf(operation.properties().get("description"));
    }

    private static String controllerTagDescription(
            final OpenApiDocs documentation,
            final String controllerName) {
        for (Map<String, Object> tag : documentation.tags()) {
            Object controllerTag = tag.get(controllerName);
            if (controllerTag instanceof Map<?, ?> metadata) {
                return String.valueOf(metadata.get("description"));
            }
        }
        return "";
    }

    private static boolean isBlank(final Object value) {
        return !(value instanceof String text) || text.isBlank();
    }

    private record ControllerOperation(Class<?> controller, Method method) {
    }
}
