package it.eng.connector.configuration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

class OpenApiDocsLoaderTest {

    private static final String DOCS_PATTERN = "classpath*:openapi/*-docs.yaml";
    private static final String EXAMPLES_PATTERN = "classpath*:openapi/examples/*.json";

    @Test
    @DisplayName("No matching resources produce empty documentation")
    void returnsEmptyDocumentationWhenNoResourcesMatch() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(DOCS_PATTERN)).thenReturn(new Resource[0]);
        when(resolver.getResources(EXAMPLES_PATTERN)).thenReturn(new Resource[0]);

        OpenApiDocs documentation = new OpenApiDocsLoader(resolver).load();

        assertTrue(documentation.tags().isEmpty());
        assertTrue(documentation.operations().isEmpty());
        assertTrue(documentation.schemas().isEmpty());
        assertTrue(documentation.examples().isEmpty());
    }

    @Test
    @DisplayName("Valid YAML loads tags operations schemas and filename-keyed examples")
    void loadsDocumentationAndExamplesFromResources() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(DOCS_PATTERN)).thenReturn(new Resource[] {
                resource("file:/openapi/tenants-docs.yaml", "tenants-docs.yaml", """
                        tags:
                          - name: Tenant
                        operations:
                          getAllTenants:
                            methodName: getAllTenants
                            methodParameters:
                              - jakarta.servlet.http.HttpServletRequest
                              - int
                              - int
                              - java.lang.String[]
                            summary: List tenants
                        schemas:
                          Tenant:
                            type: object
                        """)
        });
        when(resolver.getResources(EXAMPLES_PATTERN)).thenReturn(new Resource[] {
                resource("file:/openapi/examples/getAllTenants-plain.json",
                        "getAllTenants-plain.json", "{\"page\":1}")
        });

        OpenApiDocs documentation = new OpenApiDocsLoader(resolver).load();
        OpenApiOperationDocs operation = documentation.operations().get("getAllTenants");

        assertEquals(List.of(Map.of("name", "Tenant")), documentation.tags());
        assertEquals("getAllTenants", operation.methodName());
        assertEquals(List.of(
                "jakarta.servlet.http.HttpServletRequest",
                "int",
                "int",
                "java.lang.String[]"), operation.methodParameters().orElseThrow());
        assertEquals("List tenants", operation.properties().get("summary"));
        assertEquals("object", ((Map<?, ?>) documentation.schemas().get("Tenant")).get("type"));
        assertEquals("{\"page\":1}", documentation.examples().get("getAllTenants-plain").toString());
    }

    @Test
    @DisplayName("Malformed YAML is skipped without preventing valid resources from loading")
    void skipsMalformedYamlAndLoadsValidResources() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(DOCS_PATTERN)).thenReturn(new Resource[] {
                resource("file:/openapi/broken-docs.yaml", "broken-docs.yaml", "operations: ["),
                resource("file:/openapi/valid-docs.yaml", "valid-docs.yaml", """
                        tags:
                          - name: Available
                        operations: {}
                        schemas: {}
                        """)
        });
        when(resolver.getResources(EXAMPLES_PATTERN)).thenReturn(new Resource[0]);

        OpenApiDocs documentation = new OpenApiDocsLoader(resolver).load();

        assertEquals(List.of(Map.of("name", "Available")), documentation.tags());
    }

    @Test
    @DisplayName("Present null method names reject their resource while absent names use operation keys")
    void skipsResourceWithNullMethodNameAndFallsBackWhenMethodNameIsAbsent() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(DOCS_PATTERN)).thenReturn(new Resource[] {
                resource("file:/openapi/null-method-name-docs.yaml", "null-method-name-docs.yaml", """
                        tags:
                          - name: Invalid
                        operations:
                          invalidOperation:
                            methodName: null
                        schemas:
                          InvalidSchema: {}
                        """),
                resource("file:/openapi/valid-docs.yaml", "valid-docs.yaml", """
                        tags:
                          - name: Valid
                        operations:
                          validOperation: {}
                        schemas:
                          ValidSchema:
                            type: object
                        """)
        });
        when(resolver.getResources(EXAMPLES_PATTERN)).thenReturn(new Resource[0]);

        OpenApiDocs documentation = new OpenApiDocsLoader(resolver).load();

        assertEquals(List.of(Map.of("name", "Valid")), documentation.tags());
        assertEquals(List.of("validOperation"), List.copyOf(documentation.operations().keySet()));
        assertEquals("validOperation", documentation.operations().get("validOperation").methodName());
        assertEquals(List.of("ValidSchema"), List.copyOf(documentation.schemas().keySet()));
    }

    @Test
    @DisplayName("Explicit null sections and method parameters reject their resources")
    void skipsResourcesWithNullSectionsOrInvalidMethodParameters() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(DOCS_PATTERN)).thenReturn(new Resource[] {
                resource("file:/openapi/null-operations-docs.yaml", "null-operations-docs.yaml", """
                        tags:
                          - name: Null operations
                        operations: null
                        schemas:
                          NullOperations: {}
                        """),
                resource("file:/openapi/null-tags-docs.yaml", "null-tags-docs.yaml", """
                        tags: null
                        operations:
                          nullTags:
                            methodName: nullTags
                        schemas:
                          NullTags: {}
                        """),
                resource("file:/openapi/null-schemas-docs.yaml", "null-schemas-docs.yaml", """
                        tags:
                          - name: Null schemas
                        operations:
                          nullSchemas:
                            methodName: nullSchemas
                        schemas: null
                        """),
                resource("file:/openapi/null-method-parameters-docs.yaml",
                        "null-method-parameters-docs.yaml", """
                        tags:
                          - name: Null method parameters
                        operations:
                          nullMethodParameters:
                            methodName: nullMethodParameters
                            methodParameters: null
                        schemas: {}
                        """),
                resource("file:/openapi/scalar-method-parameters-docs.yaml",
                        "scalar-method-parameters-docs.yaml", """
                        tags:
                          - name: Scalar method parameters
                        operations:
                          scalarMethodParameters:
                            methodName: scalarMethodParameters
                            methodParameters: int
                        schemas: {}
                        """),
                resource("file:/openapi/non-string-method-parameters-docs.yaml",
                        "non-string-method-parameters-docs.yaml", """
                        tags:
                          - name: Non-string method parameters
                        operations:
                          nonStringMethodParameters:
                            methodName: nonStringMethodParameters
                            methodParameters:
                              - int
                              - 42
                        schemas: {}
                        """),
                resource("file:/openapi/valid-docs.yaml", "valid-docs.yaml", """
                        tags:
                          - name: Valid
                        operations:
                          validOperation:
                            methodName: validOperation
                            methodParameters:
                              - int
                        schemas:
                          ValidSchema:
                            type: object
                        """)
        });
        when(resolver.getResources(EXAMPLES_PATTERN)).thenReturn(new Resource[0]);

        OpenApiDocs documentation = new OpenApiDocsLoader(resolver).load();

        assertEquals(List.of(Map.of("name", "Valid")), documentation.tags());
        assertEquals(List.of("validOperation"), List.copyOf(documentation.operations().keySet()));
        assertEquals(List.of("int"),
                documentation.operations().get("validOperation").methodParameters().orElseThrow());
        assertEquals(List.of("ValidSchema"), List.copyOf(documentation.schemas().keySet()));
    }

    @Test
    @DisplayName("Documentation resources are merged in stable URL order")
    void loadsDocumentationResourcesInStableUrlOrder() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(DOCS_PATTERN)).thenReturn(new Resource[] {
                resource("file:/openapi/zulu-docs.yaml", "zulu-docs.yaml", """
                        tags:
                          - name: Zulu
                        operations:
                          zuluOperation:
                            methodName: zuluMethod
                        schemas: {}
                        """),
                resource("file:/openapi/alpha-docs.yaml", "alpha-docs.yaml", """
                        tags:
                          - name: Alpha
                        operations:
                          alphaOperation:
                            methodName: alphaMethod
                        schemas: {}
                        """)
        });
        when(resolver.getResources(EXAMPLES_PATTERN)).thenReturn(new Resource[0]);

        OpenApiDocs documentation = new OpenApiDocsLoader(resolver).load();

        assertEquals(List.of(Map.of("name", "Alpha"), Map.of("name", "Zulu")), documentation.tags());
        assertEquals(List.of("alphaOperation", "zuluOperation"),
                List.copyOf(documentation.operations().keySet()));
        assertEquals("alphaMethod", documentation.operations().get("alphaOperation").methodName());
    }

    @Test
    @DisplayName("Missing example JSON does not create an example")
    void leavesExampleAbsentWhenNoMatchingExampleResourceExists() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(DOCS_PATTERN)).thenReturn(new Resource[] {
                resource("file:/openapi/tenants-docs.yaml", "tenants-docs.yaml", """
                        tags: []
                        operations:
                          getAllTenants:
                            methodName: getAllTenants
                        schemas: {}
                        """)
        });
        when(resolver.getResources(EXAMPLES_PATTERN)).thenReturn(new Resource[0]);

        OpenApiDocs documentation = new OpenApiDocsLoader(resolver).load();

        assertTrue(documentation.examples().isEmpty());
        assertTrue(documentation.operations().get("getAllTenants").methodParameters().isEmpty());
    }

    private static Resource resource(String url, String filename, String content) throws IOException {
        return new ByteArrayResource(content.getBytes(UTF_8), filename) {
            @Override
            public URL getURL() throws IOException {
                return new URL(url);
            }

            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
