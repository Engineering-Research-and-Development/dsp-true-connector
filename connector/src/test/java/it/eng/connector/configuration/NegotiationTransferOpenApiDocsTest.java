package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.datatransfer.ftp.rest.api.FTPClientAPIController;
import it.eng.datatransfer.rest.api.DataTransferAPIController;
import it.eng.negotiation.rest.api.AgreementAPIController;
import it.eng.negotiation.rest.api.ContractNegotiationAPIController;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.RequestMapping;

class NegotiationTransferOpenApiDocsTest {

    private static final List<Class<?>> FULLY_DOCUMENTED_CONTROLLERS = List.of(
            ContractNegotiationAPIController.class,
            AgreementAPIController.class,
            DataTransferAPIController.class);
    private static final List<Class<?>> DOCUMENTED_CONTROLLERS = List.of(
            ContractNegotiationAPIController.class,
            AgreementAPIController.class,
            DataTransferAPIController.class,
            FTPClientAPIController.class);

    @Test
    @DisplayName("Negotiation and transfer operations have summaries and descriptions")
    void fullyDocumentedControllerOperationsHaveSummariesAndDescriptions() {
        OpenApiDocs documentation = docsLoader().load();
        List<String> undocumentedOperations = operations(FULLY_DOCUMENTED_CONTROLLERS).stream()
                .map(operation -> missingDocumentation(operation.controller(), operation.method(),
                        documentation.operations(), true))
                .filter(message -> !message.isEmpty())
                .toList();

        assertTrue(undocumentedOperations.isEmpty(),
                () -> "Negotiation and transfer operations are missing OpenAPI documentation: "
                        + String.join("; ", undocumentedOperations));
    }

    @Test
    @DisplayName("FTP transfer operation has a brief summary")
    void ftpOperationHasSummary() {
        OpenApiDocs documentation = docsLoader().load();
        List<String> undocumentedOperations = operations(List.of(FTPClientAPIController.class)).stream()
                .map(operation -> missingDocumentation(operation.controller(), operation.method(),
                        documentation.operations(), false))
                .filter(message -> !message.isEmpty())
                .toList();

        assertTrue(undocumentedOperations.isEmpty(),
                () -> "FTP operations are missing OpenAPI summaries: " + String.join("; ", undocumentedOperations));
    }

    @Test
    @DisplayName("Negotiation and transfer controllers have tags and generated request examples")
    void controllersHaveTagsAndExamples() {
        OpenApiDocs documentation = docsLoader().load();
        List<String> missingTags = DOCUMENTED_CONTROLLERS.stream()
                .map(Class::getSimpleName)
                .filter(controllerName -> documentation.tags().stream()
                        .noneMatch(tag -> tag.containsKey(controllerName)))
                .toList();

        assertTrue(missingTags.isEmpty(),
                () -> "Controllers are missing OpenAPI tags: " + String.join("; ", missingTags));
        assertTrue(documentation.examples().keySet().containsAll(List.of(
                        "contract-request.plain",
                        "contract-counteroffer.plain",
                        "contract-offer.plain",
                        "offer-counteroffer.plain",
                        "transfer-request.plain")),
                "OpenAPI request example references must resolve to generated module examples");
    }

    @Test
    @DisplayName("FTP controller is documented in exactly one OpenAPI YAML resource")
    void ftpControllerIsDocumentedExactlyOnce() throws IOException {
        Resource[] documentationResources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:openapi/*-docs.yaml");
        int ftpTagOccurrences = 0;
        for (Resource resource : documentationResources) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                ftpTagOccurrences += (int) reader.lines()
                        .filter(line -> line.equals("  FTPClientAPIController:"))
                        .count();
            }
        }

        assertEquals(1, ftpTagOccurrences, "FTPClientAPIController must have one module-local documentation entry");
    }

    private static OpenApiDocsLoader docsLoader() {
        return new OpenApiDocsLoader(new PathMatchingResourcePatternResolver());
    }

    private static List<ControllerOperation> operations(final List<Class<?>> controllers) {
        return controllers.stream()
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods())
                        .filter(NegotiationTransferOpenApiDocsTest::isRequestMapping)
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
                || operation.methodParameters()
                .map(expected -> !expected.equals(Arrays.stream(method.getParameterTypes())
                        .map(Class::getTypeName)
                        .toList()))
                .orElse(false)) {
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

    private static boolean isBlank(final Object value) {
        return !(value instanceof String text) || text.isBlank();
    }

    private record ControllerOperation(Class<?> controller, Method method) {
    }
}
