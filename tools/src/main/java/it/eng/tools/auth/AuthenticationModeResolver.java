package it.eng.tools.auth;

import java.util.Locale;
import java.util.Objects;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Resolves the active authentication mode from application properties.
 *
 * <p>The {@code application.auth.provider} property controls the authentication mode.
 * Supported values are {@code KEYCLOAK}, {@code INTERNAL}, and {@code DISABLED}.
 *
 * <p>When {@code application.auth.dcp.enabled=true}, protocol endpoints use DCP authentication
 * instead of the configured provider. This flag is invalid when combined with {@code DISABLED}
 * and will cause a startup failure.
 */
public final class AuthenticationModeResolver {

    /** Property that controls the active authentication provider. */
    public static final String AUTH_PROVIDER_PROPERTY = "application.auth.provider";

    /** Property that enables DCP authentication for protocol endpoints. */
    public static final String DCP_ENABLED_PROPERTY = "application.auth.dcp.enabled";

    private AuthenticationModeResolver() {
    }

    /**
     * Resolves the active authentication mode.
     *
     * @param environment the Spring environment used to read configuration
     * @return the resolved authentication mode
     * @throws IllegalStateException if the configured provider value is unsupported, or if
     *                               {@code DISABLED} and {@code dcp.enabled=true} are combined
     */
    public static AuthenticationMode resolve(Environment environment) {
        Objects.requireNonNull(environment, "environment must not be null");

        String configuredProvider = environment.getProperty(AUTH_PROVIDER_PROPERTY);
        AuthenticationMode mode = StringUtils.hasText(configuredProvider)
                ? parseProvider(configuredProvider)
                : AuthenticationMode.KEYCLOAK;

        validateDcpCombination(mode, environment);
        return mode;
    }

    /**
     * Returns whether the DCP protocol authentication is enabled.
     *
     * @param environment the Spring environment used to read configuration
     * @return {@code true} when {@code application.auth.dcp.enabled=true}
     */
    public static boolean isDcpEnabled(Environment environment) {
        Objects.requireNonNull(environment, "environment must not be null");
        return environment.getProperty(DCP_ENABLED_PROPERTY, Boolean.class, Boolean.FALSE);
    }

    private static AuthenticationMode parseProvider(String configuredProvider) {
        return switch (configuredProvider.trim().toUpperCase(Locale.ROOT)) {
            case "KEYCLOAK" -> AuthenticationMode.KEYCLOAK;
            case "INTERNAL" -> AuthenticationMode.INTERNAL;
            case "DISABLED" -> AuthenticationMode.DISABLED;
            default -> throw new IllegalStateException(
                    "Unsupported value '%s' for property '%s'. Supported values are KEYCLOAK, INTERNAL and DISABLED."
                            .formatted(configuredProvider, AUTH_PROVIDER_PROPERTY)
            );
        };
    }

    private static void validateDcpCombination(AuthenticationMode mode, Environment environment) {
        boolean dcpEnabled = environment.getProperty(DCP_ENABLED_PROPERTY, Boolean.class, Boolean.FALSE);
        if (dcpEnabled && mode == AuthenticationMode.DISABLED) {
            throw new IllegalStateException(
                    "Invalid configuration: '%s=DISABLED' and '%s=true' cannot be used together. "
                    + "DCP requires an authentication provider for the admin zone."
                            .formatted(AUTH_PROVIDER_PROPERTY, DCP_ENABLED_PROPERTY)
            );
        }
    }
}
