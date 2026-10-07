package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

class OpenApiConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfig.class)
            .withBean(BuildProperties.class, OpenApiConfigTest::buildProperties)
            .withPropertyValues(
                    "application.baseURL=https://connector.example.test",
                    "project.version=not-the-build-version");

    @Test
    @DisplayName("OpenAPI beans are absent when Swagger UI is not enabled")
    void doesNotCreateOpenApiBeansWhenSwaggerUiPropertyIsMissing() {
        contextRunner.run(context -> {
            assertEquals(0, context.getBeanNamesForType(OpenAPI.class).length);
            assertEquals(0, context.getBeanNamesForType(GroupedOpenApi.class).length);
        });
    }

    @Test
    @DisplayName("OpenAPI beans are absent when Swagger UI is explicitly disabled")
    void doesNotCreateOpenApiBeansWhenSwaggerUiIsDisabled() {
        contextRunner.withPropertyValues("springdoc.swagger-ui.enabled=false")
                .run(context -> {
                    assertEquals(0, context.getBeanNamesForType(OpenAPI.class).length);
                    assertEquals(0, context.getBeanNamesForType(GroupedOpenApi.class).length);
                });
    }

    @Test
    @DisplayName("OpenAPI beans contain build metadata and both groups when Swagger UI is enabled")
    void createsOpenApiBeansWhenSwaggerUiIsEnabled() {
        contextRunner.withPropertyValues("springdoc.swagger-ui.enabled=true")
                .run(context -> {
                    OpenAPI openAPI = context.getBean(OpenAPI.class);

                    assertEquals("0.7.4-test", openAPI.getInfo().getVersion());
                    assertEquals("https://connector.example.test", openAPI.getServers().get(0).getUrl());
                    assertEquals(SecurityScheme.Type.HTTP,
                            openAPI.getComponents().getSecuritySchemes().get("bearerAuth").getType());
                    assertEquals("bearer",
                            openAPI.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());

                    Set<String> groups = context.getBeansOfType(GroupedOpenApi.class)
                            .values().stream()
                            .map(GroupedOpenApi::getGroup)
                            .collect(Collectors.toSet());
                    assertEquals(Set.of("management-api", "dsp-protocol"), groups);
                });
    }

    @Test
    @DisplayName("Protected operations require authentication but login remains public")
    void securityCustomizerDoesNotRequireAuthenticationForAuthEndpoints() {
        contextRunner.withPropertyValues("springdoc.swagger-ui.enabled=true")
                .run(context -> {
                    OpenApiCustomizer customizer = context.getBean(
                            "securityRequirementCustomizer", OpenApiCustomizer.class);
                    Operation protectedOperation = new Operation();
                    Operation loginOperation = new Operation();
                    OpenAPI openAPI = new OpenAPI().paths(new Paths()
                            .addPathItem("/api/v1/users/me", new PathItem().get(protectedOperation))
                            .addPathItem("/api/v1/auth/login", new PathItem().post(loginOperation)));

                    customizer.customise(openAPI);

                    assertNotNull(protectedOperation.getSecurity());
                    assertTrue(protectedOperation.getSecurity().get(0).containsKey("bearerAuth"));
                    assertNull(loginOperation.getSecurity());
                });
    }

    private static BuildProperties buildProperties() {
        Properties properties = new Properties();
        properties.setProperty("version", "0.7.4-test");
        return new BuildProperties(properties);
    }
}
