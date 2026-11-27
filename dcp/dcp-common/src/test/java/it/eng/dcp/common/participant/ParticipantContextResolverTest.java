package it.eng.dcp.common.participant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticipantContextResolverTest {

    private static final Set<String> BASE_PATHS = Set.of("dcp", "issuer");
    private static final Set<String> RESERVED = Set.of("internal", "api", "actuator", ".well-known", "catalog");

    private final ParticipantContextResolver fixed = new FixedParticipantContextResolver("default");
    private final ParticipantContextResolver path = new PathParticipantContextResolver(BASE_PATHS, RESERVED);

    private static MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("POST", uri);
    }

    @Test
    @DisplayName("fixed resolver returns the configured ID for every request")
    void fixedResolvesEveryRequest() {
        assertEquals(Optional.of("default"), fixed.resolve(request("/dcp/presentations/query")));
        assertEquals(Optional.of("default"), fixed.resolve(request("/anything/else")));
    }

    @Test
    @DisplayName("path resolver returns the segment before the DCP base path")
    void pathResolvesSegmentBeforeBasePath() {
        assertEquals(Optional.of("tenant1"), path.resolve(request("/tenant1/dcp/presentations/query")));
        assertEquals(Optional.of("tenant1"), path.resolve(request("/tenant1/issuer/credentials")));
        assertEquals(Optional.of("tenant1"), path.resolve(request("/tenant1/issuer")));
    }

    @Test
    @DisplayName("path resolver ignores the servlet context path")
    void pathResolverStripsContextPath() {
        MockHttpServletRequest request = request("/app/tenant1/dcp/credentials");
        request.setContextPath("/app");

        assertEquals(Optional.of("tenant1"), path.resolve(request));
    }

    @Test
    @DisplayName("path resolver returns empty for an unscoped request")
    void pathUnscopedIsEmpty() {
        assertEquals(Optional.empty(), path.resolve(request("/dcp/presentations/query")));
        assertEquals(Optional.empty(), path.resolve(request("/issuer/credentials")));
        assertEquals(Optional.empty(), path.resolve(request("/")));
        assertEquals(Optional.empty(), path.resolve(request("/tenant1/other/thing")));
    }

    @Test
    @DisplayName("path resolver never returns a reserved ID, nor a base path")
    void pathNeverReturnsReservedId() {
        assertEquals(Optional.empty(), path.resolve(request("/internal/dcp/credentials")));
        assertEquals(Optional.empty(), path.resolve(request("/API/dcp/credentials")));
        assertEquals(Optional.empty(), path.resolve(request("/.well-known/dcp/credentials")));
        assertEquals(Optional.empty(), path.resolve(request("/catalog/dcp/credentials")));
        assertEquals(Optional.empty(), path.resolve(request("/dcp/issuer/credentials")));
        assertEquals(Optional.empty(), path.resolve(request("/issuer/dcp/credentials")));
    }

    @Test
    @DisplayName("path resolution is deterministic")
    void pathIsDeterministic() {
        MockHttpServletRequest request = request("/tenant1/dcp/credentials");

        assertEquals(path.resolve(request), path.resolve(request));
    }

    @Test
    @DisplayName("path resolver honours configured base paths")
    void pathHonoursConfiguredBasePaths() {
        ParticipantContextResolver custom = new PathParticipantContextResolver(Set.of("wallet", "iss"), RESERVED);

        assertEquals(Optional.of("tenant1"), custom.resolve(request("/tenant1/wallet/credentials")));
        assertEquals(Optional.empty(), custom.resolve(request("/tenant1/dcp/credentials")));
    }
}
