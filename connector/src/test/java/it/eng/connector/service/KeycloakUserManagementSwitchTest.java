package it.eng.connector.service;

import it.eng.connector.rest.api.KeycloakUserApiController;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryClient;
import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Checks that {@code application.keycloak.user-management} controls the Keycloak user beans.
 */
class KeycloakUserManagementSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Beans.class)
            .withPropertyValues("application.auth.provider=KEYCLOAK");

    @Test
    @DisplayName("Creates the Keycloak user beans by default")
    void defaultIsOn() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(KeycloakUserService.class);
            assertThat(context).hasSingleBean(KeycloakUserApiController.class);
        });
    }

    @Test
    @DisplayName("Creates no Keycloak user beans when user-management is false")
    void switchedOff() {
        runner.withPropertyValues("application.keycloak.user-management=false").run(context -> {
            assertThat(context).doesNotHaveBean(KeycloakUserService.class);
            assertThat(context).doesNotHaveBean(KeycloakUserApiController.class);
        });
    }

    @Test
    @DisplayName("Creates no Keycloak user beans outside Keycloak mode")
    void notKeycloakMode() {
        new ApplicationContextRunner().withUserConfiguration(Beans.class)
                .withPropertyValues("application.auth.provider=INTERNAL").run(context -> {
                    assertThat(context).doesNotHaveBean(KeycloakUserService.class);
                    assertThat(context).doesNotHaveBean(KeycloakUserApiController.class);
                });
    }

    // Deliberately not @Configuration: component scanning of the integration tests must not pick it up.
    @Import({KeycloakUserService.class, KeycloakUserApiController.class})
    static class Beans {

        @Bean
        TenantService tenantService() {
            return mock(TenantService.class);
        }

        @Bean
        KeycloakTrustProperties keycloakTrustProperties() {
            KeycloakTrustProperties properties = new KeycloakTrustProperties();
            properties.setBaseUrl("http://localhost:8080");
            return properties;
        }

        @Bean
        KeycloakDiscoveryClient keycloakDiscoveryClient() {
            return mock(KeycloakDiscoveryClient.class);
        }
    }
}
