package it.eng.connector.configuration;

import it.eng.tools.auth.keycloak.issuer.IssuerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakIssuerClassifierTest {

    private final KeycloakIssuerClassifier classifier = KeycloakRealmRoleConverterTest.classifier();

    @Test
    @DisplayName("The platform and connector-admin issuers match exactly, any other verified issuer is a tenant")
    void typeOf() {
        assertEquals(Optional.of(IssuerType.PLATFORM), classifier.typeOf("http://kc/realms/platform"));
        assertEquals(Optional.of(IssuerType.ADMIN), classifier.typeOf("http://kc/realms/connector-admin"));
        assertEquals(Optional.of(IssuerType.TENANT), classifier.typeOf("http://kc/realms/tenant-EngineeringA"));
        assertEquals(Optional.of(IssuerType.TENANT), classifier.typeOf("http://kc/realms/Platform"));
        assertTrue(classifier.typeOf(null).isEmpty());
    }

    @Test
    @DisplayName("The realm is the last path segment of an issuer URL built from the base URL")
    void realmOf() {
        assertEquals(Optional.of("platform"), classifier.realmOf("http://kc/realms/platform"));
        assertEquals(Optional.of("connector-admin"), classifier.realmOf("http://kc/realms/connector-admin"));
        assertEquals(Optional.of("tenant-EngineeringA"), classifier.realmOf("http://kc/realms/tenant-EngineeringA"));
        assertTrue(classifier.realmOf("http://other/realms/tenant-EngineeringA").isEmpty());
        assertTrue(classifier.realmOf("http://kc/realms/").isEmpty());
        assertTrue(classifier.realmOf("http://kc/realms/a/b").isEmpty());
        assertTrue(classifier.realmOf(null).isEmpty());
    }

    @Test
    @DisplayName("The trust properties are required")
    void propertiesAreRequired() {
        assertThrows(NullPointerException.class, () -> new KeycloakIssuerClassifier(null));
    }
}
