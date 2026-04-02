package it.eng.tools.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuthenticationModeResolverTest {

    @Test
    @DisplayName("Should resolve Keycloak mode from the provider property")
    void resolveKeycloakModeFromProviderProperty() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "KEYCLOAK");

        assertEquals(AuthenticationMode.KEYCLOAK, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should resolve Basic mode from the provider property")
    void resolveBasicModeFromProviderProperty() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "BASIC");

        assertEquals(AuthenticationMode.BASIC, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should resolve Disabled mode from the provider property")
    void resolveDisabledModeFromProviderProperty() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "DISABLED");

        assertEquals(AuthenticationMode.DISABLED, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should default to Keycloak mode when no property is configured")
    void resolveDefaultsToKeycloakWhenNotConfigured() {
        MockEnvironment environment = new MockEnvironment();

        assertEquals(AuthenticationMode.KEYCLOAK, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should be case-insensitive when resolving the provider property")
    void resolveIsCaseInsensitive() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "keycloak");

        assertEquals(AuthenticationMode.KEYCLOAK, AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should throw IllegalStateException for an unsupported provider value")
    void throwsForUnsupportedProviderValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "LEGACY");

        assertThrows(IllegalStateException.class, () -> AuthenticationModeResolver.resolve(environment));
    }

    @Test
    @DisplayName("Should return true for isDcpEnabled when dcp.enabled=true")
    void isDcpEnabledReturnsTrueWhenSet() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.DCP_ENABLED_PROPERTY, "true");

        assertEquals(true, AuthenticationModeResolver.isDcpEnabled(environment));
    }

    @Test
    @DisplayName("Should return false for isDcpEnabled when property is absent")
    void isDcpEnabledReturnsFalseByDefault() {
        MockEnvironment environment = new MockEnvironment();

        assertEquals(false, AuthenticationModeResolver.isDcpEnabled(environment));
    }

    @Test
    @DisplayName("Should throw IllegalStateException when DISABLED and dcp.enabled=true are combined")
    void throwsWhenDisabledAndDcpEnabledAreCombined() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AuthenticationModeResolver.AUTH_PROVIDER_PROPERTY, "DISABLED")
                .withProperty(AuthenticationModeResolver.DCP_ENABLED_PROPERTY, "true");

        assertThrows(IllegalStateException.class, () -> AuthenticationModeResolver.resolve(environment));
    }
}
