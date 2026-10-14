package it.eng.tools.auth.keycloak;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;

/**
 * Registers and validates {@link KeycloakProperties} only in {@code KEYCLOAK} authentication mode.
 */
@Configuration
@Conditional(KeycloakAuthenticationModeCondition.class)
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakConfiguration {
}
