package it.eng.connector.configuration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record OpenApiDocs(
        List<Map<String, Object>> tags,
        Map<String, OpenApiOperationDocs> operations,
        Map<String, Object> schemas,
        Map<String, String> examples) {

    OpenApiDocs {
        tags = tags.stream()
                .map(tag -> Collections.unmodifiableMap(new LinkedHashMap<>(tag)))
                .toList();
        operations = Collections.unmodifiableMap(new LinkedHashMap<>(operations));
        schemas = Collections.unmodifiableMap(new LinkedHashMap<>(schemas));
        examples = Collections.unmodifiableMap(new LinkedHashMap<>(examples));
    }
}

record OpenApiOperationDocs(
        String methodName,
        Optional<List<String>> methodParameters,
        Map<String, Object> properties) {

    OpenApiOperationDocs {
        methodParameters = methodParameters.map(List::copyOf);
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
