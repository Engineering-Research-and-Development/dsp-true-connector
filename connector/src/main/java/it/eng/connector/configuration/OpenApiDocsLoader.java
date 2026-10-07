package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads optional documentation and example resources for OpenAPI customizers.
 */
@Component
public class OpenApiDocsLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiDocsLoader.class);
    private static final String DOCS_PATTERN = "classpath*:openapi/*-docs.yaml";
    private static final String EXAMPLES_PATTERN = "classpath*:openapi/examples/*.json";

    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper objectMapper;

    /**
     * Creates a loader for classpath OpenAPI resources.
     *
     * @param resourcePatternResolver the resolver used to find classpath resources
     */
    public OpenApiDocsLoader(final ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Loads all valid OpenAPI documentation and example resources.
     *
     * @return the immutable documentation data, or empty data when no resources are available
     */
    public OpenApiDocs load() {
        List<Map<String, Object>> tags = new ArrayList<>();
        Map<String, OpenApiOperationDocs> operations = new LinkedHashMap<>();
        Map<String, Object> schemas = new LinkedHashMap<>();
        Map<String, String> examples = new LinkedHashMap<>();

        for (Resource resource : resolveResources(DOCS_PATTERN)) {
            loadDocumentation(resource).ifPresent(document -> {
                tags.addAll(document.tags());
                operations.putAll(document.operations());
                schemas.putAll(document.schemas());
            });
        }

        for (Resource resource : resolveResources(EXAMPLES_PATTERN)) {
            loadExample(resource).ifPresent(example -> examples.putIfAbsent(example.name(), example.json()));
        }

        return new OpenApiDocs(tags, operations, schemas, examples);
    }

    private List<Resource> resolveResources(final String pattern) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            return Arrays.stream(resources)
                    .sorted((left, right) -> resourceOrderKey(left).compareTo(resourceOrderKey(right)))
                    .toList();
        } catch (IOException exception) {
            LOGGER.warn("Unable to scan OpenAPI resources matching {}", pattern, exception);
            return List.of();
        }
    }

    private String resourceOrderKey(final Resource resource) {
        try {
            return resource.getURL().toExternalForm();
        } catch (IOException exception) {
            return resource.getDescription();
        }
    }

    private Optional<ParsedDocument> loadDocumentation(final Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            LoaderOptions loaderOptions = new LoaderOptions();
            Object yamlDocument = new Yaml(new SafeConstructor(loaderOptions)).load(inputStream);
            if (!(yamlDocument instanceof Map<?, ?> root)) {
                throw new IllegalArgumentException("The YAML document root must be a mapping");
            }
            return Optional.of(parseDocument(root));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Skipping invalid OpenAPI documentation resource {}", resource.getDescription(), exception);
            return Optional.empty();
        }
    }

    private ParsedDocument parseDocument(final Map<?, ?> root) {
        List<Map<String, Object>> tags = parseTags(root.get("tags"));
        Map<String, OpenApiOperationDocs> operations = parseOperations(root.get("operations"));
        Map<String, Object> schemas = parseMapping(root.get("schemas"), "schemas");
        return new ParsedDocument(tags, operations, schemas);
    }

    private List<Map<String, Object>> parseTags(final Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> tagValues)) {
            throw new IllegalArgumentException("The tags value must be a list");
        }
        List<Map<String, Object>> tags = new ArrayList<>();
        for (Object tag : tagValues) {
            if (!(tag instanceof Map<?, ?> tagMap)) {
                throw new IllegalArgumentException("Each tag must be a mapping");
            }
            tags.add(immutableStringMap(tagMap));
        }
        return List.copyOf(tags);
    }

    private Map<String, OpenApiOperationDocs> parseOperations(final Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> operationValues)) {
            throw new IllegalArgumentException("The operations value must be a mapping");
        }
        Map<String, OpenApiOperationDocs> operations = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : operationValues.entrySet()) {
            if (!(entry.getKey() instanceof String operationKey) || !(entry.getValue() instanceof Map<?, ?> fields)) {
                throw new IllegalArgumentException("Each operation must have a string key and mapping value");
            }
            Map<String, Object> properties = immutableStringMap(fields);
            Object methodNameValue = properties.get("methodName");
            if (methodNameValue != null && !(methodNameValue instanceof String)) {
                throw new IllegalArgumentException("An operation methodName must be a string");
            }
            Object methodParametersValue = properties.get("methodParameters");
            Optional<List<String>> methodParameters = parseMethodParameters(methodParametersValue);
            String methodName = methodNameValue == null ? operationKey : (String) methodNameValue;
            operations.put(operationKey, new OpenApiOperationDocs(methodName, methodParameters, properties));
        }
        return Collections.unmodifiableMap(operations);
    }

    private Optional<List<String>> parseMethodParameters(final Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof List<?> parameterValues)) {
            throw new IllegalArgumentException("Operation methodParameters must be a list");
        }
        List<String> methodParameters = new ArrayList<>();
        for (Object parameter : parameterValues) {
            if (!(parameter instanceof String parameterType)) {
                throw new IllegalArgumentException("Each method parameter type must be a string");
            }
            methodParameters.add(parameterType);
        }
        return Optional.of(List.copyOf(methodParameters));
    }

    private Map<String, Object> parseMapping(final Object value, final String name) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("The " + name + " value must be a mapping");
        }
        return immutableStringMap(map);
    }

    private Map<String, Object> immutableStringMap(final Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("OpenAPI resource mapping keys must be strings");
            }
            copy.put(key, immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private Object immutableValue(final Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableStringMap(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::immutableValue).toList();
        }
        return value;
    }

    private Optional<ParsedExample> loadExample(final Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode json = objectMapper.readTree(inputStream);
            if (json == null) {
                throw new IllegalArgumentException("The JSON example is empty");
            }
            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".json")) {
                throw new IllegalArgumentException("The JSON example must have a .json filename");
            }
            String name = filename.substring(0, filename.length() - ".json".length());
            return Optional.of(new ParsedExample(name, json.toString()));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Skipping invalid OpenAPI example resource {}", resource.getDescription(), exception);
            return Optional.empty();
        }
    }

    private record ParsedDocument(
            List<Map<String, Object>> tags,
            Map<String, OpenApiOperationDocs> operations,
            Map<String, Object> schemas) {
    }

    private record ParsedExample(String name, String json) {
    }
}
