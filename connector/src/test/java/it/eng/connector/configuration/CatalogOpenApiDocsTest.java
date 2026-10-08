package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.rest.api.ArtifactAPIController;
import it.eng.catalog.rest.api.CatalogAPIController;
import it.eng.catalog.rest.api.DataServiceAPIController;
import it.eng.catalog.rest.api.DatasetAPIController;
import it.eng.catalog.rest.api.DistributionAPIController;
import it.eng.catalog.rest.api.OfferAPIController;
import it.eng.catalog.rest.api.ProxyAPIController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

class CatalogOpenApiDocsTest {

    private static final List<Class<?>> FULLY_DOCUMENTED_CONTROLLERS = List.of(
            CatalogAPIController.class,
            DatasetAPIController.class,
            DistributionAPIController.class,
            DataServiceAPIController.class);

    private static final List<Class<?>> BRIEF_DOCUMENTED_CONTROLLERS = List.of(
            OfferAPIController.class,
            ArtifactAPIController.class,
            ProxyAPIController.class);

    @Test
    @DisplayName("Full catalog controller operations have summaries and descriptions")
    void fullCatalogControllerOperationsAreFullyDocumented() {
        OpenApiDocs documentation = new OpenApiDocsLoader(new PathMatchingResourcePatternResolver()).load();
        List<String> undocumentedOperations = operationsFor(FULLY_DOCUMENTED_CONTROLLERS).stream()
                .map(operation -> missingDocumentation(operation.controller(), operation.method(),
                        documentation.operations(), true))
                .filter(message -> !message.isEmpty())
                .toList();

        assertTrue(undocumentedOperations.isEmpty(),
                () -> "Catalog operations are missing OpenAPI documentation: "
                        + String.join("; ", undocumentedOperations));
    }

    @Test
    @DisplayName("Brief catalog controller operations have summaries")
    void briefCatalogControllerOperationsHaveSummaries() {
        OpenApiDocs documentation = new OpenApiDocsLoader(new PathMatchingResourcePatternResolver()).load();
        List<String> undocumentedOperations = operationsFor(BRIEF_DOCUMENTED_CONTROLLERS).stream()
                .map(operation -> missingDocumentation(operation.controller(), operation.method(),
                        documentation.operations(), false))
                .filter(message -> !message.isEmpty())
                .toList();

        assertTrue(undocumentedOperations.isEmpty(),
                () -> "Catalog operations are missing OpenAPI summaries: "
                        + String.join("; ", undocumentedOperations));
    }

    @Test
    @DisplayName("Every catalog controller has a named OpenAPI tag")
    void catalogControllersHaveOpenApiTags() {
        OpenApiDocs documentation = new OpenApiDocsLoader(new PathMatchingResourcePatternResolver()).load();
        List<String> missingTags = Stream
                .concat(FULLY_DOCUMENTED_CONTROLLERS.stream(), BRIEF_DOCUMENTED_CONTROLLERS.stream())
                .map(Class::getSimpleName)
                .filter(controllerName -> documentation.tags().stream()
                        .noneMatch(tag -> tag.containsKey(controllerName)))
                .toList();

        assertTrue(missingTags.isEmpty(),
                () -> "Catalog controllers are missing OpenAPI tags: " + String.join(", ", missingTags));
    }

    @Test
    @DisplayName("JSON request bodies are structured and catalog examples use resolvable names")
    void jsonRequestBodiesAreStructuredAndCatalogExamplesUseResolvableNames() {
        List<Class<?>> controllers = List.of(
                CatalogAPIController.class,
                DistributionAPIController.class,
                DataServiceAPIController.class,
                OfferAPIController.class,
                ProxyAPIController.class);
        List<String> unstructuredRequestBodies = new ArrayList<>();
        controllers.forEach(controller -> Arrays.stream(controller.getDeclaredMethods())
                .filter(CatalogOpenApiDocsTest::isRequestMapping)
                .forEach(method -> Arrays.stream(method.getParameters())
                        .filter(parameter -> parameter.isAnnotationPresent(RequestBody.class))
                        .filter(parameter -> parameter.getType() != JsonNode.class)
                        .forEach(parameter -> unstructuredRequestBodies.add(
                                controller.getSimpleName() + "." + method.getName()))));
        OpenApiDocs documentation = new OpenApiDocsLoader(new PathMatchingResourcePatternResolver()).load();

        assertTrue(unstructuredRequestBodies.isEmpty(),
                () -> "JSON request bodies should use JsonNode for OpenAPI schemas: "
                        + String.join(", ", unstructuredRequestBodies));
        assertTrue(documentation.examples().keySet().containsAll(List.of(
                        "catalog.plain",
                        "dataset.plain",
                        "distribution.plain",
                        "data-service.plain",
                        "offer.plain",
                        "forwarded-catalog.plain",
                        "forwarded-formats.plain")),
                "Generated example names should match the suffix expected by the OpenAPI customizer");
    }

    private static List<ControllerOperation> operationsFor(final List<Class<?>> controllers) {
        return controllers.stream()
                .flatMap(controller -> List.of(controller.getDeclaredMethods()).stream()
                        .filter(CatalogOpenApiDocsTest::isRequestMapping)
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

    private static boolean isBlank(final Object value) {
        return !(value instanceof String text) || text.isBlank();
    }

    private record ControllerOperation(Class<?> controller, Method method) {
    }
}
