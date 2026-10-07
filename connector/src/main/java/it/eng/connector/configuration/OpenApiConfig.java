package it.eng.connector.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.service.TenantContextHolder;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Configures the property-gated OpenAPI documentation for connector endpoints.
 */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.swagger-ui", name = "enabled", havingValue = "true")
public class OpenApiConfig {

    private static final Map<String, Integer> TAG_PRIORITIES = Map.of(
            "Auth", 0,
            "Tenant", 1,
            "User", 2,
            "Catalog", 3,
            "Dataset", 4,
            "Distribution", 5,
            "Data Service", 6,
            "Negotiation", 7,
            "Agreement", 8,
            "Transfer", 9);

    /**
     * Creates OpenAPI metadata, server configuration, and the bearer authentication scheme.
     *
     * @param buildProperties the connector build metadata
     * @param serverUrl the server URL displayed in the API documentation
     * @return the configured OpenAPI document
     */
    @Bean
    public OpenAPI openApiInfo(
            final BuildProperties buildProperties,
            @Value("${application.baseURL:/}") final String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("TRUE Connector API")
                        .version(buildProperties.getVersion())
                        .description("""
                                Authenticate with `POST /api/v1/auth/login`, then use **Authorize** to provide the \
                                returned JWT with the `bearerAuth` scheme. Use an `ADMIN` token for management \
                                endpoints and a `CONNECTOR` token for tenant-scoped DSP protocol endpoints.
                                """))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addServersItem(new Server().url(serverUrl));
    }

    /**
     * Creates the OpenAPI group for management endpoints.
     *
     * @return the management API group
     */
    @Bean
    public GroupedOpenApi managementApiGroup() {
        return GroupedOpenApi.builder()
                .group("management-api")
                .pathsToMatch("/api/**")
                .addOperationCustomizer(managementTenantHeaderCustomizer())
                .addOpenApiCustomizer(securityRequirementCustomizer())
                .addOpenApiCustomizer(tagOrderingCustomizer())
                .build();
    }

    /**
     * Creates the OpenAPI group for tenant-scoped DSP protocol endpoints.
     *
     * @return the DSP protocol API group
     */
    @Bean
    public GroupedOpenApi dspProtocolGroup() {
        return GroupedOpenApi.builder()
                .group("dsp-protocol")
                .pathsToMatch(
                        "/{tenantId}/catalog/**",
                        "/{tenantId}/negotiations/**",
                        "/{tenantId}/transfers/**",
                        "/{tenantId}/consumer/**")
                .addOpenApiCustomizer(securityRequirementCustomizer())
                .addOpenApiCustomizer(tagOrderingCustomizer())
                .build();
    }

    /**
     * Adds bearer authentication requirements to protected OpenAPI operations.
     *
     * @return the OpenAPI security customizer
     */
    @Bean
    public OpenApiCustomizer securityRequirementCustomizer() {
        return openApi -> {
            if (openApi.getPaths() != null) {
                openApi.getPaths().forEach((path, pathItem) -> {
                    if (!path.startsWith(ApiEndpoints.AUTH_V1 + "/")) {
                        pathItem.readOperations().forEach(operation ->
                                operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth")));
                    }
                });
            }
        };
    }

    /**
     * Creates a customizer that orders known workflow tags before all other tags.
     *
     * @return the OpenAPI tag-ordering customizer
     */
    @Bean
    public OpenApiCustomizer tagOrderingCustomizer() {
        return openApi -> {
            List<Tag> tags = openApi.getTags();
            if (tags != null) {
                tags.sort(Comparator
                        .comparingInt((Tag tag) -> TAG_PRIORITIES.getOrDefault(tag.getName(), Integer.MAX_VALUE))
                        .thenComparing(Tag::getName, String.CASE_INSENSITIVE_ORDER));
            }
        };
    }

    private OperationCustomizer managementTenantHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name(TenantContextHolder.HEADER_X_TENANT_ID)
                    .description("Optional tenant identifier used by super-admin requests to scope management operations.")
                    .required(false));
            return operation;
        };
    }
}
