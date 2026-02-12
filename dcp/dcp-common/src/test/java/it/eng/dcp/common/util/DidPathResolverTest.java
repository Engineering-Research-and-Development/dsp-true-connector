package it.eng.dcp.common.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DidPathResolver utility class.
 */
class DidPathResolverTest {

    @Test
    void testExtractPathSegments_NullOrBlank() {
        // Null DID
        assertEquals(Collections.emptyList(), DidPathResolver.extractPathSegments(null));

        // Blank DID
        assertEquals(Collections.emptyList(), DidPathResolver.extractPathSegments(""));
        assertEquals(Collections.emptyList(), DidPathResolver.extractPathSegments("   "));
    }

    @Test
    void testExtractPathSegments_InvalidPrefix() {
        // Not a did:web DID
        assertEquals(Collections.emptyList(), DidPathResolver.extractPathSegments("did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH"));
        assertEquals(Collections.emptyList(), DidPathResolver.extractPathSegments("https://example.com"));
    }

    @Test
    void testExtractPathSegments_NoPath() {
        // DID with no path segments (only host)
        List<String> result = DidPathResolver.extractPathSegments("did:web:localhost%3A8080");
        assertTrue(result.isEmpty(), "Expected empty list for DID with no path segments");
    }

    @Test
    void testExtractPathSegments_SinglePath() {
        // DID with single path segment
        List<String> result = DidPathResolver.extractPathSegments("did:web:localhost%3A8084:issuer");
        assertEquals(List.of("issuer"), result);
    }

    @Test
    void testExtractPathSegments_MultiplePaths() {
        // DID with multiple path segments
        List<String> result = DidPathResolver.extractPathSegments("did:web:localhost%3A8080:api:v1:issuer");
        assertEquals(List.of("api", "v1", "issuer"), result);
    }

    @Test
    void testExtractPathSegments_WithDomain() {
        // DID with domain and path
        List<String> result = DidPathResolver.extractPathSegments("did:web:example.com:holder");
        assertEquals(List.of("holder"), result);

        result = DidPathResolver.extractPathSegments("did:web:issuer.example.com:api:v2:credentials");
        assertEquals(List.of("api", "v2", "credentials"), result);
    }

    @Test
    void testExtractPathSegments_UrlEncoded() {
        // DID with URL-encoded path segments
        List<String> result = DidPathResolver.extractPathSegments("did:web:localhost%3A8080:my%20issuer");
        assertEquals(List.of("my issuer"), result);
    }

    @Test
    void testBuildWellKnownEndpointPath_EmptySegments() {
        // No path segments -> standard well-known endpoint
        String result = DidPathResolver.buildWellKnownEndpointPath(Collections.emptyList());
        assertEquals("/.well-known/did.json", result);

        // Null segments
        result = DidPathResolver.buildWellKnownEndpointPath(null);
        assertEquals("/.well-known/did.json", result);
    }

    @Test
    void testBuildWellKnownEndpointPath_SingleSegment() {
        // Single path segment
        String result = DidPathResolver.buildWellKnownEndpointPath(List.of("issuer"));
        assertEquals("/issuer/.well-known/did.json", result);
    }

    @Test
    void testBuildWellKnownEndpointPath_MultipleSegments() {
        // Multiple path segments
        String result = DidPathResolver.buildWellKnownEndpointPath(List.of("api", "v1", "issuer"));
        assertEquals("/api/v1/issuer/.well-known/did.json", result);
    }

    @Test
    void testBuildLegacyEndpointPath_EmptySegments() {
        // No path segments -> no legacy endpoint
        String result = DidPathResolver.buildLegacyEndpointPath(Collections.emptyList());
        assertNull(result);

        // Null segments
        result = DidPathResolver.buildLegacyEndpointPath(null);
        assertNull(result);
    }

    @Test
    void testBuildLegacyEndpointPath_SingleSegment() {
        // Single path segment
        String result = DidPathResolver.buildLegacyEndpointPath(List.of("issuer"));
        assertEquals("/issuer/did.json", result);
    }

    @Test
    void testBuildLegacyEndpointPath_MultipleSegments() {
        // Multiple path segments -> uses last segment
        String result = DidPathResolver.buildLegacyEndpointPath(List.of("api", "v1", "issuer"));
        assertEquals("/issuer/did.json", result);
    }

    @Test
    void testGetAllEndpointPaths_NoPath() {
        // DID with no path segments
        List<String> result = DidPathResolver.getAllEndpointPaths("did:web:localhost%3A8080");

        // Should only return well-known endpoint
        assertEquals(1, result.size());
        assertEquals("/.well-known/did.json", result.get(0));
    }

    @Test
    void testGetAllEndpointPaths_WithSinglePath() {
        // DID with single path segment
        List<String> result = DidPathResolver.getAllEndpointPaths("did:web:localhost%3A8084:issuer");

        // Should return W3C with path, legacy, and fallback well-known
        assertEquals(3, result.size());
        assertEquals("/issuer/.well-known/did.json", result.get(0)); // W3C with path
        assertEquals("/issuer/did.json", result.get(1));              // Legacy
        assertEquals("/.well-known/did.json", result.get(2));         // Fallback
    }

    @Test
    void testGetAllEndpointPaths_WithMultiplePaths() {
        // DID with multiple path segments
        List<String> result = DidPathResolver.getAllEndpointPaths("did:web:localhost%3A8080:api:v1:issuer");

        // Should return W3C with path, legacy (last segment), and fallback well-known
        assertEquals(3, result.size());
        assertEquals("/api/v1/issuer/.well-known/did.json", result.get(0)); // W3C with deep path
        assertEquals("/issuer/did.json", result.get(1));                     // Legacy (last segment)
        assertEquals("/.well-known/did.json", result.get(2));                // Fallback
    }

    @Test
    void testExtractRoleName_EmptySegments() {
        // No path segments -> default "connector"
        String result = DidPathResolver.extractRoleName(Collections.emptyList());
        assertEquals("connector", result);

        // Null segments
        result = DidPathResolver.extractRoleName(null);
        assertEquals("connector", result);
    }

    @Test
    void testExtractRoleName_SingleSegment() {
        // Single path segment
        String result = DidPathResolver.extractRoleName(List.of("issuer"));
        assertEquals("issuer", result);
    }

    @Test
    void testExtractRoleName_MultipleSegments() {
        // Multiple path segments -> returns last segment
        String result = DidPathResolver.extractRoleName(List.of("api", "v1", "issuer"));
        assertEquals("issuer", result);
    }

    @Test
    void testEndToEnd_StandardDidResolution() {
        // Test complete flow for standard DID (no path)
        String did = "did:web:localhost%3A8080";
        List<String> pathSegments = DidPathResolver.extractPathSegments(did);

        assertTrue(pathSegments.isEmpty());
        assertEquals("/.well-known/did.json", DidPathResolver.buildWellKnownEndpointPath(pathSegments));
        assertNull(DidPathResolver.buildLegacyEndpointPath(pathSegments));
        assertEquals("connector", DidPathResolver.extractRoleName(pathSegments));
    }

    @Test
    void testEndToEnd_IssuerDidResolution() {
        // Test complete flow for issuer DID with path
        String did = "did:web:localhost%3A8084:issuer";
        List<String> pathSegments = DidPathResolver.extractPathSegments(did);

        assertEquals(List.of("issuer"), pathSegments);
        assertEquals("/issuer/.well-known/did.json", DidPathResolver.buildWellKnownEndpointPath(pathSegments));
        assertEquals("/issuer/did.json", DidPathResolver.buildLegacyEndpointPath(pathSegments));
        assertEquals("issuer", DidPathResolver.extractRoleName(pathSegments));
    }

    @Test
    void testEndToEnd_DeepPathDidResolution() {
        // Test complete flow for DID with deep path structure
        String did = "did:web:localhost%3A8080:api:v1:credentials:issuer";
        List<String> pathSegments = DidPathResolver.extractPathSegments(did);

        assertEquals(List.of("api", "v1", "credentials", "issuer"), pathSegments);
        assertEquals("/api/v1/credentials/issuer/.well-known/did.json",
                     DidPathResolver.buildWellKnownEndpointPath(pathSegments));
        assertEquals("/issuer/did.json", DidPathResolver.buildLegacyEndpointPath(pathSegments));
        assertEquals("issuer", DidPathResolver.extractRoleName(pathSegments));
    }
}
