package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.rest.api.CatalogAPIController;
import it.eng.catalog.rest.protocol.CatalogController;
import it.eng.connector.rest.api.KeycloakUserApiController;
import it.eng.tools.rest.api.TenantAPIController;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

class OpenApiDocsDriftValidatorTest {

    @Test
    @DisplayName("Empty documentation passes validation")
    void acceptsEmptyDocumentation() {
        OpenApiDocs documentation = new OpenApiDocs(List.of(), Map.of(), Map.of(), Map.of());

        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(documentation, Map.of()));
    }

    @Test
    @DisplayName("An operation reference matching a registered method passes validation")
    void acceptsKnownOperationId() throws NoSuchMethodException {
        HandlerMethod unpagedHandler = handlerMethod("getAllTenants");
        HandlerMethod pagedHandler = handlerMethod(
                "getAllTenants", HttpServletRequest.class, int.class, int.class, String[].class);
        OpenApiDocs documentation = documentation("getAllTenants", Optional.empty());

        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(
                documentation, handlerMethods(unpagedHandler, pagedHandler)));
    }

    @Test
    @DisplayName("A documented handler on a conditional controller passes validation")
    void acceptsOperationOnConditionalController() throws NoSuchMethodException {
        OpenApiDocs documentation = documentation(
                "getUsers", Optional.of(List.of("java.lang.String")));

        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(
                documentation, Map.of(), KeycloakUserApiController.class));
    }

    @Test
    @DisplayName("Controller-scoped operation keys disambiguate methods with the same name")
    void acceptsControllerScopedOperationId() throws NoSuchMethodException {
        Method apiMethod = CatalogAPIController.class.getDeclaredMethod("getCatalog");
        Method protocolMethod = CatalogController.class.getDeclaredMethod(
                "getCatalog", String.class, String.class, JsonNode.class);
        HandlerMethod apiHandler = new HandlerMethod(mock(CatalogAPIController.class), apiMethod);
        HandlerMethod protocolHandler = new HandlerMethod(mock(CatalogController.class), protocolMethod);
        OpenApiOperationDocs operation = new OpenApiOperationDocs(
                "getCatalog",
                Optional.empty(),
                Map.of("controllerName", "CatalogController"));
        OpenApiDocs documentation = new OpenApiDocs(
                List.of(),
                Map.of("CatalogController.getCatalog", operation),
                Map.of(),
                Map.of());

        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(
                documentation, handlerMethods(apiHandler, protocolHandler)));
    }

    @Test
    @DisplayName("An overloaded operation selector must match a registered parameter signature")
    void requiresOverloadedOperationSelectorToMatchRegisteredSignature() throws NoSuchMethodException {
        HandlerMethod pagedHandler = handlerMethod(
                "getAllTenants", HttpServletRequest.class, int.class, int.class, String[].class);
        List<String> parameterTypes = List.of(
                "jakarta.servlet.http.HttpServletRequest",
                "int",
                "int",
                "java.lang.String[]");
        OpenApiDocs matchingDocumentation = documentation(
                "getAllTenants", Optional.of(parameterTypes));
        OpenApiDocs mismatchedDocumentation = documentation(
                "getAllTenants", Optional.of(List.of("jakarta.servlet.http.HttpServletRequest", "int")));

        assertDoesNotThrow(() -> OpenApiDocsDriftValidator.validate(
                matchingDocumentation, handlerMethods(pagedHandler)));
        AssertionError error = assertThrows(AssertionError.class,
                () -> OpenApiDocsDriftValidator.validate(
                        mismatchedDocumentation,
                        handlerMethods(pagedHandler, handlerMethod("getAllTenants"))));

        assertTrue(error.getMessage().contains("getAllTenants"));
        assertTrue(error.getMessage().contains("methodParameters"));
    }

    @Test
    @DisplayName("An unknown operation ID fails with identifying context")
    void rejectsUnknownOperationId() {
        OpenApiDocs documentation = documentation("missingOperation", Optional.empty());

        AssertionError error = assertThrows(AssertionError.class,
                () -> OpenApiDocsDriftValidator.validate(documentation, Map.of()));

        assertTrue(error.getMessage().contains("missingOperation"));
        assertTrue(error.getMessage().contains("methodName"));
    }

    private static OpenApiDocs documentation(
            String operationId,
            Optional<List<String>> methodParameters) {
        OpenApiOperationDocs operation = new OpenApiOperationDocs(
                operationId, methodParameters, Map.of());
        return new OpenApiDocs(List.of(), Map.of(operationId, operation), Map.of(), Map.of());
    }

    private static Map<RequestMappingInfo, HandlerMethod> handlerMethods(HandlerMethod... handlerMethods) {
        Map<RequestMappingInfo, HandlerMethod> mappings = new LinkedHashMap<>();
        for (int index = 0; index < handlerMethods.length; index++) {
            mappings.put(RequestMappingInfo.paths("/test/" + index).build(), handlerMethods[index]);
        }
        return Map.copyOf(mappings);
    }

    private static HandlerMethod handlerMethod(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = TenantAPIController.class.getDeclaredMethod(methodName, parameterTypes);
        return new HandlerMethod(mock(TenantAPIController.class), method);
    }
}
