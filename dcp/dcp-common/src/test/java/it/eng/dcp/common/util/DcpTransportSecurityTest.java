package it.eng.dcp.common.util;

import it.eng.dcp.common.exception.InsecureEndpointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DcpTransportSecurityTest {

    @Test
    @DisplayName("scheme is https by default and when TLS is enabled")
    void scheme_defaultsToHttps() {
        assertEquals("https", DcpTransportSecurity.scheme(false, false));
        assertEquals("https", DcpTransportSecurity.scheme(false, true));
        assertEquals("https", DcpTransportSecurity.scheme(true, true));
    }

    @Test
    @DisplayName("scheme is http only when insecure http is allowed and TLS is off")
    void scheme_httpWhenAllowedAndNoTls() {
        assertEquals("http", DcpTransportSecurity.scheme(true, false));
    }

    @Test
    @DisplayName("https endpoint is accepted")
    void requireSecure_https_ok() {
        assertDoesNotThrow(() -> DcpTransportSecurity.requireSecure("https://issuer.example.com", "x", false));
        assertDoesNotThrow(() -> DcpTransportSecurity.requireSecure("HTTPS://issuer.example.com", "x", false));
    }

    @Test
    @DisplayName("http endpoint is rejected by default")
    void requireSecure_http_rejected() {
        InsecureEndpointException ex = assertThrows(InsecureEndpointException.class,
                () -> DcpTransportSecurity.requireSecure("http://issuer.example.com", "IssuerService", false));
        assertEquals(true, ex.getMessage().contains("http://issuer.example.com"));
    }

    @Test
    @DisplayName("http endpoint is accepted when insecure http is allowed")
    void requireSecure_http_allowed() {
        assertDoesNotThrow(() -> DcpTransportSecurity.requireSecure("http://issuer.example.com", "x", true));
    }

    @Test
    @DisplayName("other schemes are rejected even when insecure http is allowed")
    void requireSecure_otherScheme_rejected() {
        assertThrows(InsecureEndpointException.class,
                () -> DcpTransportSecurity.requireSecure("ftp://issuer.example.com", "x", true));
    }

    @Test
    @DisplayName("blank, null and scheme-less endpoints are rejected")
    void requireSecure_invalid_rejected() {
        assertThrows(InsecureEndpointException.class, () -> DcpTransportSecurity.requireSecure(null, "x", true));
        assertThrows(InsecureEndpointException.class, () -> DcpTransportSecurity.requireSecure(" ", "x", true));
        assertThrows(InsecureEndpointException.class,
                () -> DcpTransportSecurity.requireSecure("issuer.example.com", "x", true));
        assertThrows(InsecureEndpointException.class,
                () -> DcpTransportSecurity.requireSecure("ht tp://bad", "x", true));
    }
}
