package it.eng.tools.util;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CredentialUtils.
 *
 * <p>These tests focus on URL normalization logic to validate the string-based implementation
 * that handles all URL formats correctly including localhost, domain names, IPv4, and IPv6 addresses.
 *
 * <p>The tests cover:
 * <ul>
 *   <li>Localhost URLs with and without ports</li>
 *   <li>Domain names with and without ports</li>
 *   <li>IPv4 addresses with various port configurations</li>
 *   <li>IPv6 addresses (with required brackets)</li>
 *   <li>Standard port omission (80 for HTTP, 443 for HTTPS)</li>
 *   <li>Query parameters and fragments handling</li>
 * </ul>
 */
class CredentialUtilsTest {

    private CredentialUtils credentialUtils;
    private Method normalizeUrlMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Create a minimal CredentialUtils instance
        OkHttpClient okHttpClient = new OkHttpClient();
        credentialUtils = new CredentialUtils(okHttpClient, null, null);

        // Get access to the private normalizeUrl method using reflection
        normalizeUrlMethod = CredentialUtils.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeUrlMethod.setAccessible(true);
    }

    @Test
    @DisplayName("Compare URI vs URL behavior with localhost")
    public void compareUriVsUrlWithLocalhost() throws Exception {
        String urlString = "http://localhost:8090";

        // Test with URI
        URI uri = URI.create(urlString);

        // Test with URL
        URL url = new URL(urlString);

        // Assertions to verify both work correctly
        assertNotNull(uri.getHost(), "URI.getHost() should not be null for localhost");
        assertNotNull(url.getHost(), "URL.getHost() should not be null for localhost");

        assertEquals("localhost", uri.getHost(), "URI should parse localhost correctly");
        assertEquals("localhost", url.getHost(), "URL should parse localhost correctly");

        assertEquals(8090, uri.getPort(), "URI should parse port correctly");
        assertEquals(8090, url.getPort(), "URL should parse port correctly");

        assertEquals("http", uri.getScheme(), "URI should parse scheme correctly");
        assertEquals("http", url.getProtocol(), "URL should parse protocol correctly");

        System.out.println("\n✅ Both URI and URL work correctly with localhost!");
    }
    /**
     * Invokes the private normalizeUrl method from CredentialUtils.
     *
     * @param url The URL to normalize
     * @return The normalized URL
     */
    private String invokeNormalizeUrl(String url) {
        try {
            return (String) normalizeUrlMethod.invoke(credentialUtils, url);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke normalizeUrl method", e);
        }
    }

    @Test
    @DisplayName("Verify URI.create works correctly with localhost")
    void verifyUriCreateWorksWithLocalhost() {
        // Test to verify that URI.create() actually DOES work correctly with localhost
        String url = "http://localhost:8090/catalog/request";
        URI uri = URI.create(url);

        // Verify that getHost() returns "localhost" correctly
        String host = uri.getHost();

        assertNotNull(host, "URI.create() works correctly - getHost() should not be null");
        assertEquals("localhost", host, "Host should be parsed as 'localhost'");
        assertEquals(8090, uri.getPort(), "Port should be parsed as 8090");
        assertEquals("http", uri.getScheme(), "Scheme should be 'http'");
    }

    @Test
    @DisplayName("Test URL normalization - localhost with port")
    void testNormalizeUrl_LocalhostWithPort() {
        String url = "http://localhost:8090/catalog/request";
        String expected = "http://localhost:8090";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from localhost with port");
    }

    @Test
    @DisplayName("Test URL normalization - localhost with port")
    void testNormalizeUrl_Localhost() {
        String url = "http://localhost:8090";
        String expected = "http://localhost:8090";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from localhost with port");
    }

    @Test
    @DisplayName("Test URL normalization - localhost without port")
    void testNormalizeUrl_LocalhostWithoutPort() {
        String url = "http://localhost/catalog/request";
        String expected = "http://localhost";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from localhost without port");
    }

    @Test
    @DisplayName("Test URL normalization - domain with custom port")
    void testNormalizeUrl_DomainWithPort() {
        String url = "https://example.com:9090/dsp/catalog";
        String expected = "https://example.com:9090";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from domain with custom port");
    }

    @Test
    @DisplayName("Test URL normalization - domain without port")
    void testNormalizeUrl_DomainWithoutPort() {
        String url = "https://example.com/catalog";
        String expected = "https://example.com";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from domain without port");
    }

    @Test
    @DisplayName("Test URL normalization - HTTP with standard port 80 should omit port")
    void testNormalizeUrl_HttpWithStandardPort() {
        String url = "http://example.com:80/catalog";
        String expected = "http://example.com";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should omit standard port 80 for HTTP");
    }

    @Test
    @DisplayName("Test URL normalization - HTTPS with standard port 443 should omit port")
    void testNormalizeUrl_HttpsWithStandardPort() {
        String url = "https://example.com:443/catalog";
        String expected = "https://example.com";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should omit standard port 443 for HTTPS");
    }

    @Test
    @DisplayName("Test URL normalization - URL with multiple path segments")
    void testNormalizeUrl_MultiplePathSegments() {
        String url = "http://localhost:8090/v1/catalog/datasets/123";
        String expected = "http://localhost:8090";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should extract base URL regardless of path depth");
    }

    @Test
    @DisplayName("Test URL normalization - URL without path")
    void testNormalizeUrl_NoPath() {
        String url = "http://localhost:8090";
        String expected = "http://localhost:8090";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should handle URL without path correctly");
    }

    @Test
    @DisplayName("Test URL normalization - URL with query parameters")
    void testNormalizeUrl_WithQueryParameters() {
        String url = "http://localhost:8090/catalog?filter=active";
        String expected = "http://localhost:8090";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should extract base URL and ignore query parameters");
    }

    @Test
    @DisplayName("Test URL normalization - IPv4 address with port")
    void testNormalizeUrl_IPv4WithPort() {
        String url = "http://192.168.1.100:8080/catalog/request";
        String expected = "http://192.168.1.100:8080";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from IPv4 address with port");
    }

    @Test
    @DisplayName("Test URL normalization - IPv4 address without port")
    void testNormalizeUrl_IPv4WithoutPort() {
        String url = "http://192.168.1.100/catalog/request";
        String expected = "http://192.168.1.100";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from IPv4 address without port");
    }

    @Test
    @DisplayName("Test URL normalization - IPv4 address with standard port 80")
    void testNormalizeUrl_IPv4WithStandardPort80() {
        String url = "http://10.0.0.1:80/api/data";
        String expected = "http://10.0.0.1";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should omit standard port 80 for HTTP with IPv4");
    }

    @Test
    @DisplayName("Test URL normalization - IPv4 address with standard port 443")
    void testNormalizeUrl_IPv4WithStandardPort443() {
        String url = "https://172.16.0.1:443/secure/api";
        String expected = "https://172.16.0.1";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should omit standard port 443 for HTTPS with IPv4");
    }

    @Test
    @DisplayName("Test URL normalization - IPv6 address with port")
    void testNormalizeUrl_IPv6WithPort() {
        String url = "http://[2001:db8::1]:8080/catalog/request";
        String expected = "http://[2001:db8::1]:8080";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from IPv6 address with port");
    }

    @Test
    @DisplayName("Test URL normalization - IPv6 address without port")
    void testNormalizeUrl_IPv6WithoutPort() {
        String url = "http://[2001:db8::1]/catalog/request";
        String expected = "http://[2001:db8::1]";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from IPv6 address without port");
    }

    @Test
    @DisplayName("Test URL normalization - IPv6 localhost with port")
    void testNormalizeUrl_IPv6LocalhostWithPort() {
        String url = "http://[::1]:9090/api/data";
        String expected = "http://[::1]:9090";
        String actual = invokeNormalizeUrl(url);
        assertEquals(expected, actual, "Should correctly extract base URL from IPv6 localhost with port");
    }

    @Test
    @DisplayName("Verify URI.create works correctly with IPv4 address")
    void verifyUriCreateWorksWithIPv4() {
        String url = "http://192.168.1.100:8080/catalog/request";
        URI uri = URI.create(url);

        assertNotNull(uri.getHost(), "URI.create() should parse IPv4 host correctly");
        assertEquals("192.168.1.100", uri.getHost(), "Host should be parsed as IPv4 address");
        assertEquals(8080, uri.getPort(), "Port should be parsed as 8080");
        assertEquals("http", uri.getScheme(), "Scheme should be 'http'");
    }

    @Test
    @DisplayName("Verify URI.create works correctly with IPv6 address")
    void verifyUriCreateWorksWithIPv6() {
        String url = "http://[2001:db8::1]:8080/catalog/request";
        URI uri = URI.create(url);

        assertNotNull(uri.getHost(), "URI.create() should parse IPv6 host correctly");
        // Note: URI.getHost() returns IPv6 addresses WITH brackets
        assertEquals("[2001:db8::1]", uri.getHost(), "Host should be parsed as IPv6 address (with brackets)");
        assertEquals(8080, uri.getPort(), "Port should be parsed as 8080");
        assertEquals("http", uri.getScheme(), "Scheme should be 'http'");
    }

    @Test
    @DisplayName("Get API credentials - returns basic auth")
    void getAPICredentials_returnsBasicAuth() {
        // Act
        String credentials = credentialUtils.getAPICredentials();

        // Assert
        assertNotNull(credentials);
        assertTrue(credentials.startsWith("Basic "));
    }

    @Test
    @DisplayName("Get connector credentials without DCP services - fallback to basic auth")
    void getConnectorCredentials_noDcpServices_fallbackToBasicAuth() {
        // Act
        String credentials = credentialUtils.getConnectorCredentials();

        // Assert
        assertNotNull(credentials);
        assertTrue(credentials.startsWith("Basic "));
    }
}





