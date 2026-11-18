package it.eng.tools.auth.keycloak.issuer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuerRegistrySettingsTest {

    private static final Duration SECOND = Duration.ofSeconds(1);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    @Test
    @DisplayName("The default settings are explicit and consistent")
    void defaults() {
        IssuerRegistrySettings settings = IssuerRegistrySettings.defaults();
        assertEquals(Duration.ofMinutes(5), settings.jwksCacheTimeToLive());
        assertEquals(Duration.ofSeconds(30), settings.jwksRefreshAheadTime());
        assertEquals(Duration.ofSeconds(15), settings.jwksCacheRefreshTimeout());
        assertEquals(Duration.ofSeconds(30), settings.jwksRateLimitInterval());
        assertEquals(Duration.ofSeconds(5), settings.failureRetryInterval());
    }

    @Test
    @DisplayName("Non-positive and inconsistent values are rejected")
    void invalidSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new IssuerRegistrySettings(0, MINUTE, SECOND, MINUTE, SECOND, SECOND, SECOND, 1024));
        assertThrows(IllegalArgumentException.class,
                () -> new IssuerRegistrySettings(10, MINUTE, Duration.ZERO, MINUTE, SECOND, SECOND, SECOND, 1024));
        assertThrows(NullPointerException.class,
                () -> new IssuerRegistrySettings(10, null, SECOND, MINUTE, SECOND, SECOND, SECOND, 1024));
        assertThrows(IllegalArgumentException.class,
                () -> new IssuerRegistrySettings(10, MINUTE, SECOND, MINUTE, SECOND, SECOND, SECOND, 0));
        // Refresh-ahead plus refresh timeout exceeds the time-to-live.
        assertThrows(IllegalArgumentException.class,
                () -> new IssuerRegistrySettings(10, MINUTE, SECOND, MINUTE, MINUTE, SECOND, SECOND, 1024));
        // Rate limit interval not shorter than the time-to-live.
        assertThrows(IllegalArgumentException.class,
                () -> new IssuerRegistrySettings(10, MINUTE, SECOND, MINUTE, SECOND, SECOND, MINUTE, 1024));
    }
}
