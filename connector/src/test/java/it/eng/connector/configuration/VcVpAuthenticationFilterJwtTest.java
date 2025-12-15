package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.*;

import it.eng.dcp.core.DidResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.dcp.model.PresentationResponseMessage;
import jakarta.servlet.FilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Test for VcVpAuthenticationFilter JWT parsing capabilities.
 */
@Slf4j
class VcVpAuthenticationFilterJwtTest {

    private VcVpAuthenticationFilter filter;
    private ObjectMapper objectMapper;
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        authenticationManager = authentication -> {
            // Mock authentication manager that just returns the input
            return authentication;
        };
        filter = new VcVpAuthenticationFilter(authenticationManager, objectMapper, true);
    }

    @Test
    void testParseJwtTokenWithVpClaim() throws Exception {
        // Given: A real JWT token with VP in the "vp" claim (from user's example)
        String jwtToken = "eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W3sidHlwZSI6Ik1lbWJlcnNoaXBDcmVkZW50aWFsIiwiZm9ybWF0Ijoiand0Iiwiand0IjoiZXlKcmFXUWlPaUp2T1ZkemRUTjJTRjlMVFhNM1ZFNVVNRkE0VURsS1RIUklNbUpSWldVdGJWUndOVkJsZVVkRE5IUTRJaXdpWVd4bklqb2lSVk15TlRZaWZRLmV5SnpkV0lpT2lKa2FXUTZkMlZpT214dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYVhOeklqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0ltVjRjQ0k2TVRjNU56QTNOamcwT1N3aWFXRjBJam94TnpZMU5UUXdPRFE1TENKMll5STZleUpqY21Wa1pXNTBhV0ZzVTNWaWFtVmpkQ0k2ZXlKdFpXMWlaWEp6YUdsd1ZIbHdaU0k2SWxCeVpXMXBkVzBpTENKdFpXMWlaWEp6YUdsd1NXUWlPaUpOUlUxQ1JWSXRZelF5TUdJM01qY2lMQ0pwWkNJNkltUnBaRHAzWldJNmJHOWpZV3hvYjNOME9qZ3dPREFpTENKemRHRjBkWE1pT2lKQlkzUnBkbVVpZlN3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazFsYldKbGNuTm9hWEJEY21Wa1pXNTBhV0ZzSWwwc0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5bGVHRnRjR3hsTG05eVp5OWpjbVZrWlc1MGFXRnNjeTkyTVNKZGZTd2lhblJwSWpvaWRYSnVPblYxYVdRNk56QmpOakZtWlRFdFl6TTVaQzAwWldVMUxUa3dNVEV0WXpZMk5tWmlNV1psTURobUluMC5GcnlwU1FzM2xoSi1GOFlzMzRYZWNJeUttVzc1OERrMl92MVh2NnliUnh4b2t3Q1VyRllSNXNlLXlvRDNQUVUxQWo4ajk4S3lSeE5va09TTENSclgxQSJ9XSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTU0NzM1NywianRpIjoidXJuOnV1aWQ6ODc1YmM2YjEtNDg0ZC00NmU0LWEwZGYtYmMyYmFiNDM1OThhIn0.ibDZR1GlX9g0mj_uLxAH_03imYoEx8FYmE8ze2aeFcITZL4oCP0d_spgYgZ8EJG7Pecu24BBaXAkfulePOwOog";

        // Create a mock request with the JWT token
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtToken);
        request.setRequestURI("/connector/test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (req, res) -> {
            // Filter chain continuation
        };

        // When: Filter processes the request
        // We can't directly test the private method, but we can verify it doesn't throw exceptions
        assertDoesNotThrow(() -> {
            filter.doFilterInternal(request, response, filterChain);
        });

        log.info("JWT token parsed successfully without exceptions");
    }

    @Test
    void testJwtTokenStructure() throws Exception {
        // Given: The same JWT token
        String jwtToken = "eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W3sidHlwZSI6Ik1lbWJlcnNoaXBDcmVkZW50aWFsIiwiZm9ybWF0Ijoiand0Iiwiand0IjoiZXlKcmFXUWlPaUp2T1ZkemRUTjJTRjlMVFhNM1ZFNVVNRkE0VURsS1RIUklNbUpSWldVdGJWUndOVkJsZVVkRE5IUTRJaXdpWVd4bklqb2lSVk15TlRZaWZRLmV5SnpkV0lpT2lKa2FXUTZkMlZpT214dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYVhOeklqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0ltVjRjQ0k2TVRjNU56QTNOamcwT1N3aWFXRjBJam94TnpZMU5UUXdPRFE1TENKMll5STZleUpqY21Wa1pXNTBhV0ZzVTNWaWFtVmpkQ0k2ZXlKdFpXMWlaWEp6YUdsd1ZIbHdaU0k2SWxCeVpXMXBkVzBpTENKdFpXMWlaWEp6YUdsd1NXUWlPaUpOUlUxQ1JWSXRZelF5TUdJM01qY2lMQ0pwWkNJNkltUnBaRHAzWldJNmJHOWpZV3hvYjNOME9qZ3dPREFpTENKemRHRjBkWE1pT2lKQlkzUnBkbVVpZlN3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazFsYldKbGNuTm9hWEJEY21Wa1pXNTBhV0ZzSWwwc0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5bGVHRnRjR3hsTG05eVp5OWpjbVZrWlc1MGFXRnNjeTkyTVNKZGZTd2lhblJwSWpvaWRYSnVPblYxYVdRNk56QmpOakZtWlRFdFl6TTVaQzAwWldVMUxUa3dNVEV0WXpZMk5tWmlNV1psTURobUluMC5GcnlwU1FzM2xoSi1GOFlzMzRYZWNJeUttVzc1OERrMl92MVh2NnliUnh4b2t3Q1VyRllSNXNlLXlvRDNQUVUxQWo4ajk4S3lSeE5va09TTENSclgxQSJ9XSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTU0NzM1NywianRpIjoidXJuOnV1aWQ6ODc1YmM2YjEtNDg0ZC00NmU0LWEwZGYtYmMyYmFiNDM1OThhIn0.ibDZR1GlX9g0mj_uLxAH_03imYoEx8FYmE8ze2aeFcITZL4oCP0d_spgYgZ8EJG7Pecu24BBaXAkfulePOwOog";

        // When: Parse the JWT using Nimbus
        com.nimbusds.jwt.JWT jwt = com.nimbusds.jwt.JWTParser.parse(jwtToken);

        // Then: Verify the structure
        assertNotNull(jwt);
        assertNotNull(jwt.getJWTClaimsSet());

        // Verify claims
        assertEquals("did:web:localhost:8080", jwt.getJWTClaimsSet().getIssuer());
        assertEquals("did:web:localhost:8080", jwt.getJWTClaimsSet().getSubject());

        // Verify VP claim exists
        Object vpClaim = jwt.getJWTClaimsSet().getClaim("vp");
        assertNotNull(vpClaim, "VP claim should be present in JWT");

        // Verify VP structure
        assertTrue(vpClaim instanceof java.util.Map, "VP claim should be a Map");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> vp = (java.util.Map<String, Object>) vpClaim;

        assertTrue(vp.containsKey("verifiableCredential"), "VP should contain verifiableCredential");
        assertTrue(vp.containsKey("profileId"), "VP should contain profileId");
        assertEquals("VC11_SL2021_JWT", vp.get("profileId"));

        log.info("JWT structure verified successfully");
        log.info("VP claim: {}", vp);
    }

    @Test
    void testVcVpEnabledFalseSkipsProcessing() throws Exception {
        // Given: Filter with vcVpEnabled=false
        VcVpAuthenticationFilter disabledFilter = new VcVpAuthenticationFilter(authenticationManager, objectMapper, false);

        String jwtToken = "eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W3sidHlwZSI6Ik1lbWJlcnNoaXBDcmVkZW50aWFsIiwiZm9ybWF0Ijoiand0Iiwiand0IjoiZXlKcmFXUWlPaUp2T1ZkemRUTjJTRjlMVFhNM1ZFNVVNRkE0VURsS1RIUklNbUpSWldVdGJWUndOVkJsZVVkRE5IUTRJaXdpWVd4bklqb2lSVk15TlRZaWZRLmV5SnpkV0lpT2lKa2FXUTZkMlZpT214dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYVhOeklqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0ltVjRjQ0k2TVRjNU56QTNOamcwT1N3aWFXRjBJam94TnpZMU5UUXdPRFE1TENKMll5STZleUpqY21Wa1pXNTBhV0ZzVTNWaWFtVmpkQ0k2ZXlKdFpXMWlaWEp6YUdsd1ZIbHdaU0k2SWxCeVpXMXBkVzBpTENKdFpXMWlaWEp6YUdsd1NXUWlPaUpOUlUxQ1JWSXRZelF5TUdJM01qY2lMQ0pwWkNJNkltUnBaRHAzWldJNmJHOWpZV3hvYjNOME9qZ3dPREFpTENKemRHRjBkWE1pT2lKQlkzUnBkbVVpZlN3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazFsYldKbGNuTm9hWEJEY21Wa1pXNTBhV0ZzSWwwc0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5bGVHRnRjR3hsTG05eVp5OWpjbVZrWlc1MGFXRnNjeTkyTVNKZGZTd2lhblJwSWpvaWRYSnVPblYxYVdRNk56QmpOakZtWlRFdFl6TTVaQzAwWldVMUxUa3dNVEV0WXpZMk5tWmlNV1psTURobUluMC5GcnlwU1FzM2xoSi1GOFlzMzRYZWNJeUttVzc1OERrMl92MVh2NnliUnh4b2t3Q1VyRllSNXNlLXlvRDNQUVUxQWo4ajk4S3lSeE5va09TTENSclgxQSJ9XSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTU0NzM1NywianRpIjoidXJuOnV1aWQ6ODc1YmM2YjEtNDg0ZC00NmU0LWEwZGYtYmMyYmFiNDM1OThhIn0.ibDZR1GlX9g0mj_uLxAH_03imYoEx8FYmE8ze2aeFcITZL4oCP0d_spgYgZ8EJG7Pecu24BBaXAkfulePOwOog";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtToken);
        request.setRequestURI("/connector/test");

        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean[] filterChainCalled = {false};
        FilterChain filterChain = (req, res) -> {
            filterChainCalled[0] = true;
        };

        // When: Filter processes the request
        disabledFilter.doFilterInternal(request, response, filterChain);

        // Then: Filter chain should be called (skipped VP processing)
        assertTrue(filterChainCalled[0], "Filter chain should be called when VC/VP is disabled");

        log.info("Filter correctly skipped VP processing when disabled");
    }
}

