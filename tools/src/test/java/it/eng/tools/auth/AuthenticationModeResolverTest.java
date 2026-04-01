package it.eng.tools.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuthenticationModeResolverTest {

    @Test
    @DisplayName("Should resolve Keycloak mode from the new provider property")
    void resolveKeycloakModeFromProviderProperty() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "KEYCLOAK");

        assertEquals(AuthenticationMode.KEYCLOAK, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should resolve disabled mode from the new provider property")
    void resolveDisabledModeFromProviderProperty() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "DISABLED");

        assertEquals(AuthenticationMode.DISABLED, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should let the new provider property override the legacy Keycloak flag")
    void resolveProviderPropertyWithLegacyOverride() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "DISABLED")
                .withProperty(AuthenticationModeResolver.LEGACY_KEYCLOAK_ENABLED_PROPERTY, "true");

        assertEquals(AuthenticationMode.DISABLED, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should resolve Keycloak mode from the legacy property when the new property is absent")
    void resolveLegacyKeycloakMode() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.LEGACY_KEYCLOAK_ENABLED_PROPERTY, "true");

        assertEquals(AuthenticationMode.KEYCLOAK, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should resolve legacy mode when no authentication properties are configured")
    void resolveLegacyModeByDefault() {
        MockEnvironment environment = new MockEnvironment();

        assertEquals(AuthenticationMode.LEGACY, AuthenticationModeResolver.resolve(environment));
    }
}
