package it.eng.connector.configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Validates YAML operation references against registered Spring MVC handlers in tests.
 */
public final class OpenApiDocsDriftValidator {

    private OpenApiDocsDriftValidator() {
    }

    /**
     * Validates loaded documentation against the application's Spring MVC handler registry.
     *
     * @param docsLoader the loader for optional OpenAPI documentation resources
     * @param handlerMapping the Spring MVC handler registry
     * @throws AssertionError if a documented operation does not match a registered handler
     */
    public static void validate(
            final OpenApiDocsLoader docsLoader,
            final RequestMappingHandlerMapping handlerMapping) {
        validate(docsLoader.load(), handlerMapping.getHandlerMethods());
    }

    static void validate(
            final OpenApiDocs documentation,
            final Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
        validate(documentation, handlerMethods, new Class<?>[0]);
    }

    /**
     * Validates registered handlers and declared request mappings on explicitly supplied conditional controllers.
     *
     * @param documentation the loaded OpenAPI documentation
     * @param handlerMethods the active Spring MVC handler registry
     * @param conditionalControllerTypes controller types that may be inactive in the current authentication mode
     * @throws AssertionError if a documented operation does not match a registered or conditional handler
     */
    static void validate(
            final OpenApiDocs documentation,
            final Map<RequestMappingInfo, HandlerMethod> handlerMethods,
            final Class<?>... conditionalControllerTypes) {
        List<String> unmappedOperations = documentation.operations().entrySet().stream()
                .filter(entry -> !hasMatchingHandler(
                        entry.getKey(), entry.getValue(), handlerMethods, conditionalControllerTypes))
                .map(entry -> describeUnmappedOperation(entry.getKey(), entry.getValue()))
                .toList();
        if (!unmappedOperations.isEmpty()) {
            throw new AssertionError("OpenAPI documentation references unmapped operations: "
                    + String.join("; ", unmappedOperations));
        }
    }

    private static boolean hasMatchingHandler(
            final String operationId,
            final OpenApiOperationDocs operation,
            final Map<RequestMappingInfo, HandlerMethod> handlerMethods,
            final Class<?>[] conditionalControllerTypes) {
        if (!operationId.equals(operation.methodName())) {
            return false;
        }
        boolean registeredHandlerMatches = handlerMethods.values().stream().anyMatch(handlerMethod -> {
            if (!operationId.equals(handlerMethod.getMethod().getName())) {
                return false;
            }
            return operation.methodParameters()
                    .map(expected -> expected.equals(Arrays.stream(handlerMethod.getMethod().getParameterTypes())
                            .map(Class::getTypeName)
                            .toList()))
                    .orElse(true);
        });
        if (registeredHandlerMatches) {
            return true;
        }
        return Arrays.stream(conditionalControllerTypes)
                .flatMap(controllerType -> Arrays.stream(controllerType.getDeclaredMethods()))
                .filter(method -> operationId.equals(method.getName()))
                .filter(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null)
                .anyMatch(method -> operation.methodParameters()
                        .map(expected -> expected.equals(Arrays.stream(method.getParameterTypes())
                                .map(Class::getTypeName)
                                .toList()))
                        .orElse(true));
    }

    private static String describeUnmappedOperation(
            final String operationId,
            final OpenApiOperationDocs operation) {
        String methodParameters = operation.methodParameters()
                .map(parameters -> parameters.stream().collect(Collectors.joining(", ", "[", "]")))
                .orElse("unspecified");
        return "operationId '" + operationId + "' (methodName '" + operation.methodName()
                + "', methodParameters " + methodParameters + ")";
    }
}
