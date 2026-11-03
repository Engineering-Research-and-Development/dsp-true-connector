package it.eng.tools.auth.keycloak;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakTrustPropertiesTest {

    private KeycloakTrustProperties valid() {
        KeycloakTrustProperties props = new KeycloakTrustProperties();
        props.setBaseUrl("http://localhost:8180");
        return props;
    }

    @Test
    void defaults() {
        KeycloakTrustProperties props = valid();
        assertDoesNotThrow(props::init);
        assertEquals("dsp-true-connector", props.getAudience());
        assertEquals("platform", props.getPlatformRealm());
        assertEquals("connector-admin", props.getAdminRealm());
        assertEquals("dsp-connector-backend", props.getPlatformClientId());
        assertEquals("keycloak-api", props.getUiClientId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    void blankUiClientIdFailsStartup(String clientId) {
        KeycloakTrustProperties props = valid();
        props.setUiClientId(clientId);
        assertThrows(IllegalStateException.class, props::init);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    void blankPlatformClientIdFailsStartup(String clientId) {
        KeycloakTrustProperties props = valid();
        props.setPlatformClientId(clientId);
        assertThrows(IllegalStateException.class, props::init);
    }

    @Test
    void trailingSlashIsRemoved() {
        KeycloakTrustProperties props = new KeycloakTrustProperties();
        props.setBaseUrl("https://kc.example.com/auth//");
        assertEquals("https://kc.example.com/auth", props.getBaseUrl());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "kc.example.com", "ftp://kc.example.com", "/realms", "http://", "http://a b" })
    void invalidBaseUrlFailsStartup(String baseUrl) {
        KeycloakTrustProperties props = new KeycloakTrustProperties();
        props.setBaseUrl(baseUrl);
        assertThrows(IllegalStateException.class, props::init);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "a/b", "..", ".", "a b", "realm?x" })
    void invalidRealmFailsStartup(String realm) {
        KeycloakTrustProperties platform = valid();
        platform.setPlatformRealm(realm);
        assertThrows(IllegalStateException.class, platform::init);
        KeycloakTrustProperties admin = valid();
        admin.setAdminRealm(realm);
        assertThrows(IllegalStateException.class, admin::init);
    }

    @Test
    void invalidTimeoutFailsStartup() {
        KeycloakTrustProperties props = valid();
        props.setDiscoveryReadTimeout(Duration.ZERO);
        assertThrows(IllegalStateException.class, props::init);
    }

    @Test
    void issuerFor() {
        KeycloakTrustProperties props = valid();
        assertEquals("http://localhost:8180/realms/platform", props.issuerFor("platform"));
        assertEquals("http://localhost:8180/realms/tenant-a.b_c", props.issuerFor("tenant-a.b_c"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "a/b", "..", ".", "a\\b", "a%2fb", "a?b", "a#b" })
    void issuerForRejectsInvalidRealm(String realm) {
        assertThrows(IllegalArgumentException.class, () -> valid().issuerFor(realm));
    }
}
