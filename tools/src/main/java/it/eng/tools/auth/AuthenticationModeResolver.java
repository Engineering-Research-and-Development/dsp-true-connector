package it.eng.tools.auth;

import java.util.Locale;
import java.util.Objects;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Resolves the active authentication mode from application properties.
 *
 * <p>The new {@code application.auth.provider} property takes precedence over the
 * legacy {@code application.keycloak.enable} switch. When the new property is not
 * configured, the resolver falls back to the previous boolean-based behavior for
 * backward compatibility.
 */
public final class AuthenticationModeResolver {

    public static final String AUTH_PROVIDER_PROPERTY = "application.auth.provider";
    public static final String LEGACY_KEYCLOAK_ENABLED_PROPERTY = "application.keycloak.enable";

    private AuthenticationModeResolver() {
    }

    /**
     * Resolves the active authentication mode.
     *
     * @param environment the Spring environment used to read configuration
     * @return the resolved authentication mode
     * @throws IllegalStateException if the configured provider value is unsupported
     */
    public static AuthenticationMode resolve(Environment environment) {
        Objects.requireNonNull(environment, "environment must not be null");

        String configuredProvider = environment.getProperty(AUTH_PROVIDER_PROPERTY);
        if (StringUtils.hasText(configuredProvider)) {
            return parseProvider(configuredProvider);
        }

        boolean keycloakEnabled = environment.getProperty(LEGACY_KEYCLOAK_ENABLED_PROPERTY, Boolean.class, Boolean.FALSE);
        return keycloakEnabled ? AuthenticationMode.KEYCLOAK : AuthenticationMode.LEGACY;
    }

    private static AuthenticationMode parseProvider(String configuredProvider) {
        return switch (configuredProvider.trim().toUpperCase(Locale.ROOT)) {
            case "KEYCLOAK" -> AuthenticationMode.KEYCLOAK;
            case "DISABLED" -> AuthenticationMode.DISABLED;
            default -> throw new IllegalStateException(
                    "Unsupported value '%s' for property '%s'. Supported values are KEYCLOAK and DISABLED."
                            .formatted(configuredProvider, AUTH_PROVIDER_PROPERTY)
            );
        };
    }
}
