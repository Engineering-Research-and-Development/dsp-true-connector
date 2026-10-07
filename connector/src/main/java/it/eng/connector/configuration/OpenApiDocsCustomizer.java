package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Applies connector OpenAPI documentation resources to generated operations and schemas.
 */
public final class OpenApiDocsCustomizer implements OperationCustomizer, GlobalOpenApiCustomizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiDocsCustomizer.class);
    private static final String PLAIN_EXAMPLE_SUFFIX = ".plain";
    private static final String PROTOCOL_EXAMPLE_SUFFIX = ".protocol";
    private static final String COMPONENT_EXAMPLE_REFERENCE = "#/components/examples/";

    private final OpenApiDocs documentation;
    private final RequestMappingHandlerMapping handlerMapping;
    private final Map<String, JsonNode> examples;

    /**
     * Loads OpenAPI documentation and examples for connector handlers.
     *
     * @param docsLoader the loader for optional documentation resources
     * @param handlerMapping the registry of Spring MVC handlers and their paths
     */
    public OpenApiDocsCustomizer(
            final OpenApiDocsLoader docsLoader,
            final RequestMappingHandlerMapping handlerMapping) {
        this(docsLoader.load(), handlerMapping);
    }

    OpenApiDocsCustomizer(final OpenApiDocs documentation, final RequestMappingHandlerMapping handlerMapping) {
        this.documentation = documentation;
        this.handlerMapping = handlerMapping;
        this.examples = parseExamples(documentation.examples());
    }

    /**
     * Applies YAML documentation to the operation associated with the handler method.
     *
     * @param operation the generated OpenAPI operation
     * @param handlerMethod the Spring MVC handler for the operation
     * @return the customized operation
     */
    @Override
    public Operation customize(final Operation operation, final HandlerMethod handlerMethod) {
        Optional<OpenApiOperationDocs> operationDocs = findOperationDocs(handlerMethod);
        if (operationDocs.isEmpty()) {
            return operation;
        }

        OpenApiOperationDocs docs = operationDocs.get();
        Map<String, Object> properties = docs.properties();
        setStringProperty(properties, "summary", operation::setSummary);
        setStringProperty(properties, "description", operation::setDescription);
        if (properties.containsKey("responses")) {
            readResponses(properties.get("responses"), handlerMethod.getMethod().getName())
                    .ifPresent(operation::setResponses);
        }
        operation.addTagsItem(resolveTagName(handlerMethod.getBeanType().getSimpleName()));
        applyRequestExamples(operation, handlerMethod, properties.get("requestExamples"));
        return operation;
    }

    /**
     * Applies controller tag metadata, schema field descriptions, and referenced examples.
     *
     * @param openApi the generated OpenAPI document
     */
    @Override
    public void customise(final OpenAPI openApi) {
        if (!documentation.tags().isEmpty() && openApi.getTags() == null) {
            openApi.setTags(new ArrayList<>());
        }
        addDocumentedTags(openApi);
        applySchemaDescriptions(openApi);
        registerReferencedExamples(openApi);
    }

    private Map<String, JsonNode> parseExamples(final Map<String, String> loadedExamples) {
        Map<String, JsonNode> parsedExamples = new LinkedHashMap<>();
        loadedExamples.forEach((name, json) -> {
            try {
                JsonNode value = Json.mapper().readTree(json);
                if (value == null) {
                    LOGGER.warn("Skipping empty OpenAPI example resource {}", name);
                } else {
                    parsedExamples.put(name, value);
                }
            } catch (IOException exception) {
                LOGGER.warn("Skipping invalid OpenAPI example resource {}", name, exception);
            }
        });
        return Map.copyOf(parsedExamples);
    }

    private Optional<OpenApiOperationDocs> findOperationDocs(final HandlerMethod handlerMethod) {
        OpenApiOperationDocs operationDocs =
                documentation.operations().get(handlerMethod.getMethod().getName());
        if (operationDocs == null || !operationDocs.methodName().equals(handlerMethod.getMethod().getName())) {
            return Optional.empty();
        }
        return methodParametersMatch(operationDocs, handlerMethod.getMethod())
                ? Optional.of(operationDocs)
                : Optional.empty();
    }

    private boolean methodParametersMatch(final OpenApiOperationDocs operationDocs, final Method method) {
        return operationDocs.methodParameters()
                .map(expected -> expected.equals(Arrays.stream(method.getParameterTypes())
                        .map(Class::getTypeName)
                        .toList()))
                .orElse(true);
    }

    private void setStringProperty(
            final Map<String, Object> properties,
            final String key,
            final Consumer<String> setter) {
        Object value = properties.get(key);
        if (value instanceof String text) {
            setter.accept(text);
        } else if (value != null) {
            LOGGER.warn("Ignoring non-string OpenAPI operation property {}", key);
        }
    }

    private Optional<ApiResponses> readResponses(final Object value, final String operationId) {
        if (!(value instanceof Map<?, ?>)) {
            LOGGER.warn("Ignoring non-mapping responses for OpenAPI operation {}", operationId);
            return Optional.empty();
        }
        try {
            return Optional.of(Json.mapper().convertValue(value, ApiResponses.class));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Ignoring malformed responses for OpenAPI operation {}", operationId, exception);
            return Optional.empty();
        }
    }

    private void applyRequestExamples(
            final Operation operation,
            final HandlerMethod handlerMethod,
            final Object requestExamplesValue) {
        Map<String, String> requestExamples = requestExampleNames(requestExamplesValue);
        RequestBody requestBody = operation.getRequestBody();
        Content content = requestBody == null ? null : requestBody.getContent();
        if (content == null || requestExamples.isEmpty()) {
            return;
        }

        String suffix = exampleSuffix(handlerMethod);
        for (Map.Entry<String, String> requestExample : requestExamples.entrySet()) {
            String exampleName = normalizedExampleName(requestExample.getValue(), suffix);
            if (!examples.containsKey(exampleName)) {
                continue;
            }
            Example exampleReference = new Example()
                    .$ref(COMPONENT_EXAMPLE_REFERENCE + exampleName);
            content.values().forEach(mediaType -> addExample(mediaType, requestExample.getKey(), exampleReference));
        }
    }

    private Map<String, String> requestExampleNames(final Object value) {
        Map<String, String> names = new LinkedHashMap<>();
        if (value instanceof String exampleName) {
            names.put(exampleName, exampleName);
        } else if (value instanceof List<?> exampleNames) {
            for (Object item : exampleNames) {
                if (item instanceof String exampleName) {
                    names.put(exampleName, exampleName);
                } else {
                    LOGGER.warn("Skipping non-string request example reference {}", item);
                }
            }
        } else if (value instanceof Map<?, ?> exampleNames) {
            exampleNames.forEach((label, exampleName) -> {
                if (label instanceof String exampleLabel && exampleName instanceof String exampleValue) {
                    names.put(exampleLabel, exampleValue);
                } else {
                    LOGGER.warn("Skipping invalid request example reference {}", label);
                }
            });
        } else if (value != null) {
            LOGGER.warn("Skipping unsupported requestExamples value of type {}", value.getClass().getName());
        }
        return names;
    }

    private void addExample(final MediaType mediaType, final String label, final Example example) {
        if (mediaType.getExamples() == null) {
            mediaType.setExamples(new LinkedHashMap<>());
        }
        mediaType.addExamples(label, example);
    }

    private String normalizedExampleName(final String name, final String suffix) {
        if (name.endsWith(PLAIN_EXAMPLE_SUFFIX) || name.endsWith(PROTOCOL_EXAMPLE_SUFFIX)) {
            return name.substring(0, name.lastIndexOf('.')) + suffix;
        }
        return name + suffix;
    }

    private String exampleSuffix(final HandlerMethod handlerMethod) {
        return pathsFor(handlerMethod).stream().anyMatch(OpenApiDocsCustomizer::isManagementPath)
                ? PLAIN_EXAMPLE_SUFFIX
                : PROTOCOL_EXAMPLE_SUFFIX;
    }

    private static boolean isManagementPath(final String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }

    private List<String> pathsFor(final HandlerMethod handlerMethod) {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().equals(handlerMethod))
                .flatMap(entry -> entry.getKey().getPatternValues().stream())
                .toList();
    }

    private String resolveTagName(final String controllerName) {
        return controllerTagMetadata(controllerName)
                .map(metadata -> metadata.get("name"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(controllerName);
    }

    private Optional<Map<String, Object>> controllerTagMetadata(final String controllerName) {
        for (Map<String, Object> tag : documentation.tags()) {
            Object controllerMetadata = tag.get(controllerName);
            if (controllerMetadata instanceof Map<?, ?> metadata) {
                return Optional.of(stringObjectMap(metadata));
            }
            if (controllerName.equals(tag.get("name"))) {
                return Optional.of(tag);
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> stringObjectMap(final Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> mapping) {
            mapping.forEach((key, item) -> {
                if (key instanceof String name) {
                    result.put(name, item);
                }
            });
        }
        return result;
    }

    private void addDocumentedTags(final OpenAPI openApi) {
        for (Map<String, Object> tag : documentation.tags()) {
            if (tag.get("name") instanceof String name) {
                addTag(openApi, name, tag);
                continue;
            }
            for (Map.Entry<String, Object> entry : tag.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> metadata) {
                    addTag(openApi, entry.getKey(), stringObjectMap(metadata));
                } else {
                    LOGGER.warn("Ignoring invalid OpenAPI tag metadata for key {}", entry.getKey());
                }
            }
        }
    }

    private void addTag(final OpenAPI openApi, final String controllerName, final Map<String, Object> metadata) {
        String tagName = metadata.get("name") instanceof String configuredName
                ? configuredName
                : controllerName;
        Tag matchingTag = openApi.getTags().stream()
                .filter(tag -> tagName.equals(tag.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Tag newTag = new Tag().name(tagName);
                    openApi.addTagsItem(newTag);
                    return newTag;
                });
        if (metadata.get("description") instanceof String description) {
            matchingTag.setDescription(description);
        }
    }

    private void applySchemaDescriptions(final OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        documentation.schemas().forEach((schemaName, fieldDescriptions) -> {
            Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
            if (schema == null || !(fieldDescriptions instanceof Map<?, ?> fields)) {
                return;
            }
            Map<?, ?> properties = fields.get("fields") instanceof Map<?, ?> nestedFields
                    ? nestedFields
                    : fields.get("properties") instanceof Map<?, ?> nestedProperties
                            ? nestedProperties
                            : fields;
            properties.forEach((fieldName, description) -> {
                if (fieldName instanceof String name && schema.getProperties() != null
                        && schema.getProperties().get(name) instanceof Schema<?> propertySchema) {
                    schemaFieldDescription(description).ifPresent(propertySchema::setDescription);
                }
            });
        });
    }

    private Optional<String> schemaFieldDescription(final Object value) {
        if (value instanceof String description) {
            return Optional.of(description);
        }
        if (value instanceof Map<?, ?> details && details.get("description") instanceof String description) {
            return Optional.of(description);
        }
        return Optional.empty();
    }

    private void registerReferencedExamples(final OpenAPI openApi) {
        Set<String> referencedExamples = new LinkedHashSet<>();
        documentation.operations().values().forEach(operationDocs -> {
            Object requestExamples = operationDocs.properties().get("requestExamples");
            requestExampleNames(requestExamples).values().forEach(name -> {
                String suffix = exampleSuffix(operationDocs);
                String exampleName = normalizedExampleName(name, suffix);
                if (examples.containsKey(exampleName)) {
                    referencedExamples.add(exampleName);
                }
            });
        });
        if (referencedExamples.isEmpty()) {
            return;
        }
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        referencedExamples.forEach(name -> openApi.getComponents()
                .addExamples(name, new Example().value(examples.get(name))));
    }

    private String exampleSuffix(final OpenApiOperationDocs operationDocs) {
        Optional<HandlerMethod> handlerMethod = handlerMapping.getHandlerMethods().values().stream()
                .filter(candidate -> candidate.getMethod().getName().equals(operationDocs.methodName()))
                .filter(candidate -> methodParametersMatch(operationDocs, candidate.getMethod()))
                .findFirst();
        return handlerMethod.map(this::exampleSuffix).orElse(PROTOCOL_EXAMPLE_SUFFIX);
    }
}
