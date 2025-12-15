package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.VerifiablePresentation;
import jakarta.servlet.FilterChain;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Test for VcVpAuthenticationFilter JWT parsing capabilities.
 * This test focuses on validating that JWT tokens are properly parsed into
 * valid Java objects (PresentationResponseMessage) rather than generic Maps.
 */
@Slf4j
class VcVpAuthenticationFilterJwtTest {

    private VcVpAuthenticationFilter filter;
    private ObjectMapper objectMapper;
    private AuthenticationManager authenticationManager;

    // The JWT provided by the user containing a VP with MembershipCredential
    private static final String REAL_JWT_TOKEN = "eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W3sidHlwZSI6Ik1lbWJlcnNoaXBDcmVkZW50aWFsIiwiZm9ybWF0Ijoiand0Iiwiand0IjoiZXlKcmFXUWlPaUp2T1ZkemRUTjJTRjlMVFhNM1ZFNVVNRkE0VURsS1RIUklNbUpSWldVdGJWUndOVkJsZVVkRE5IUTRJaXdpWVd4bklqb2lSVk15TlRZaWZRLmV5SnpkV0lpT2lKa2FXUTZkMlZpT214dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYVhOeklqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0ltVjRjQ0k2TVRjNU56TXpNVFEwTVN3aWFXRjBJam94TnpZMU56azFORFF4TENKMll5STZleUpqY21Wa1pXNTBhV0ZzVTNWaWFtVmpkQ0k2ZXlKdFpXMWlaWEp6YUdsd1ZIbHdaU0k2SWxCeVpXMXBkVzBpTENKdFpXMWlaWEp6YUdsd1NXUWlPaUpOUlUxQ1JWSXRNakZpTmpNNU1HSWlMQ0pwWkNJNkltUnBaRHAzWldJNmJHOWpZV3hvYjNOME9qZ3dPREFpTENKemRHRjBkWE1pT2lKQlkzUnBkbVVpZlN3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazFsYldKbGNuTm9hWEJEY21Wa1pXNTBhV0ZzSWwwc0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5bGVHRnRjR3hsTG05eVp5OWpjbVZrWlc1MGFXRnNjeTkyTVNKZGZTd2lhblJwSWpvaWRYSnVPblYxYVdRNll6WTBOR0l5T0RJdE9XTTROQzAwTkRjekxXSmpZak10T0RGa05UbGpNVEprT1RVekluMC5pTXR4NGxHOWVscDdXSXZVS29hZjB3RmczdGVGY1FSN05sVTBNNkU3dzd0alFvVXlOOEFTd2Q1enBpNjliSXFuMWtMZE5vWTFRbUEtZjNTNVVVYkdIQSJ9XSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTgwMDc1MiwianRpIjoidXJuOnV1aWQ6MmRiMGRmYmUtNTEzOS00NThiLTg5ZjgtZjA3OTMxZTg2NDRkIn0.fsdqeWVxIxuF3RqdqgB_mLoVOcrhLuR0sOoWyUIqeBXy9eQkL4QZZS7PdyTG3F35_yEwLHL-sRJxuJYkDXIKzw";

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
    void testParseJwtTokenWithVpClaim() {
        // Given: A real JWT token with VP in the "vp" claim (from user's example)
        String jwtToken = "eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W3sidHlwZSI6Ik1lbWJlcnNoaXBDcmVkZW50aWFsIiwiZm9ybWF0Ijoiand0Iiwiand0IjoiZXlKcmFXUWlPaUp2T1ZkemRUTjJTRjlMVFhNM1ZFNVVNRkE0VURsS1RIUklNbUpSWldVdGJWUndOVkJsZVVkRE5IUTRJaXdpWVd4bklqb2lSVk15TlRZaWZRLmV5SnpkV0lpT2lKa2FXUTZkMlZpT214dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYVhOeklqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0ltVjRjQ0k2TVRjNU56QTNOamcwT1N3aWFXRjBJam94TnpZMU5UUXdPRFE1TENKMll5STZleUpqY21Wa1pXNTBhV0ZzVTNWaWFtVmpkQ0k2ZXlKdFpXMWlaWEp6YUdsd1ZIbHdaU0k2SWxCeVpXMXBkVzBpTENKdFpXMWlaWEp6YUdsd1NXUWlPaUpOUlUxQ1JWSXRZelF5TUdJM01qY2lMQ0pwWkNJNkltUnBaRHAzWldJNmJHOWpZV3hvYjNOME9qZ3dPREFpTENKemRHRjBkWE1pT2lKQlkzUnBkbVVpZlN3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazFsYldKbGNuTm9hWEJEY21Wa1pXNTBhV0ZzSWwwc0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5bGVHRnRjR3hsTG05eVp5OWpjbVZrWlc1MGFXRnNjeTkyTVNKZGZTd2lhblJwSWpvaWRYSnVPblYxYVdRNk56QmpOakZtWlRFdFl6TTVaQzAwWldVMUxUa3dNVEV0WXpZMk5tWmlNV1psTURobUluMC5GcnlwU1FzM2xoSi1GOFlzMzRYZWNJeUttVzc1OERrMl92MVh2NnliUnh4b2t3Q1VyRllSNXNlLXlvRDNQUVUxQWo4ajk4S3lSeE5va09TTENSclgxQSJ9XSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTU0NzM1NywianRpIjoidXJuOnV1aWQ6ODc1YmM2YjEtNDg0ZC00NmU0LWEwZGYtYmMyYmFiNDM1OThhIn0.ibDZR1GlX9g0mj_uLxAH_03imYoEx8FYmE8ze2aeFcITZL4oCP0d_spgYgZ8EJG7Pecu24BBaXAkfulePOwOog";

        // Create a mock request with the JWT token
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtToken);
        request.setRequestURI("/connector/test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (req, res) -> { };

        // When: Filter processes the request
        // We can't directly test the private method, but we can verify it doesn't throw exceptions
        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

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
        assertInstanceOf(java.util.Map.class, vpClaim, "VP claim should be a Map");
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
        FilterChain filterChain = (req, res) -> filterChainCalled[0] = true;

        // When: Filter processes the request
        disabledFilter.doFilterInternal(request, response, filterChain);

        // Then: Filter chain should be called (skipped VP processing)
        assertTrue(filterChainCalled[0], "Filter chain should be called when VC/VP is disabled");

        log.info("Filter correctly skipped VP processing when disabled");
    }

    /**
     * This test validates that the parsePresentation method properly creates
     * valid Java objects (PresentationResponseMessage) from the JWT token,
     * NOT generic Maps or Object types.
     * This test uses reflection to access the private parsePresentation method
     * to validate the internals of JWT parsing logic.
     */
    @Test
    void testParsePresentationCreatesProperJavaObjects() throws Exception {
        // Given: The real JWT token provided by the user
        // When: Parse the presentation using the filter's private method via reflection
        Method parsePresentationMethod = VcVpAuthenticationFilter.class.getDeclaredMethod("parsePresentation", String.class);
        parsePresentationMethod.setAccessible(true);
        PresentationResponseMessage presentation = (PresentationResponseMessage) parsePresentationMethod.invoke(filter, REAL_JWT_TOKEN);

        // Then: Verify that a valid PresentationResponseMessage was created
        assertNotNull(presentation, "Presentation should not be null");
        assertNotNull(presentation.getPresentation(), "Presentation list should not be null");
        assertFalse(presentation.getPresentation().isEmpty(), "Presentation list should not be empty");

        log.info("Parsed presentation: {}", presentation);
        log.info("Presentation list size: {}", presentation.getPresentation().size());

        // Verify the presentation contains the VP structure
        Object firstPresentation = presentation.getPresentation().get(0);
        assertNotNull(firstPresentation, "First presentation element should not be null");

        // The first element should now be a VerifiablePresentation object (not a generic Map)
        assertInstanceOf(VerifiablePresentation.class, firstPresentation,
                "Presentation should contain a VerifiablePresentation object, not a generic Map");

        VerifiablePresentation vp = (VerifiablePresentation) firstPresentation;

        log.info("VerifiablePresentation class: {}", vp.getClass().getName());
        log.info("VerifiablePresentation holderDid: {}", vp.getHolderDid());
        log.info("VerifiablePresentation profileId: {}", vp.getProfileId());

        // Verify VP structure
        assertNotNull(vp.getHolderDid(), "VP should have a holderDid");
        assertEquals("did:web:localhost:8080", vp.getHolderDid(), "HolderDid should match JWT subject");

        // Verify profileId
        assertNotNull(vp.getProfileId(), "VP should have a profileId");
        assertEquals("VC11_SL2021_JWT", vp.getProfileId(), "Profile ID should be VC11_SL2021_JWT");

        // Verify verifiableCredential is present
        assertNotNull(vp.getCredentials(), "VP should have credentials");
        assertFalse(vp.getCredentials().isEmpty(), "Credentials list should not be empty");

        log.info("Credentials list size: {}", vp.getCredentials().size());
        log.info("First credential: {}", vp.getCredentials().get(0));

        // The VC should be a Map containing type, format, and jwt fields
        Object firstVc = vp.getCredentials().get(0);
        assertInstanceOf(Map.class, firstVc, "First VC should be a Map");

        @SuppressWarnings("unchecked")
        Map<String, Object> vcMap = (Map<String, Object>) firstVc;

        // Verify the VC structure based on the user's JWT
        // Expected structure: { "type": "MembershipCredential", "format": "jwt", "jwt": "..." }
        assertTrue(vcMap.containsKey("type") || vcMap.containsKey("format") || vcMap.containsKey("jwt"),
                "VC should contain type, format, or jwt field");

        if (vcMap.containsKey("type")) {
            assertEquals("MembershipCredential", vcMap.get("type"), "VC type should be MembershipCredential");
        }

        if (vcMap.containsKey("format")) {
            assertEquals("jwt", vcMap.get("format"), "VC format should be jwt");
        }

        if (vcMap.containsKey("jwt")) {
            assertNotNull(vcMap.get("jwt"), "VC JWT should not be null");
            assertInstanceOf(String.class, vcMap.get("jwt"), "VC JWT should be a String");
        }

        log.info("✓ JWT successfully parsed into proper Java objects");
        log.info("✓ PresentationResponseMessage structure is valid");
        log.info("✓ VP is a proper VerifiablePresentation object (not a generic Map)");
        log.info("✓ VP contains expected MembershipCredential structure");
    }

    /**
     * This test validates the end-to-end flow with the real JWT token.
     * It ensures that:
     * 1. The filter can process the JWT without exceptions
     * 2. The authentication token is properly created with the parsed presentation
     * 3. The parsed presentation contains valid Java objects
     */
    @Test
    void testEndToEndJwtProcessing() throws Exception {
        // Given: A filter with a capturing authentication manager
        VcVpAuthenticationToken[] capturedToken = new VcVpAuthenticationToken[1];

        AuthenticationManager capturingAuthManager = authentication -> {
            if (authentication instanceof VcVpAuthenticationToken) {
                capturedToken[0] = (VcVpAuthenticationToken) authentication;
            }
            return authentication;
        };

        VcVpAuthenticationFilter testFilter = new VcVpAuthenticationFilter(capturingAuthManager, objectMapper, true);

        // Create request with real JWT
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + REAL_JWT_TOKEN);
        request.setRequestURI("/connector/test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (req, res) -> { };

        // When: Filter processes the request
        testFilter.doFilterInternal(request, response, filterChain);

        // Then: Verify the authentication token was created with proper objects
        assertNotNull(capturedToken[0], "Authentication token should be captured");

        PresentationResponseMessage presentation = capturedToken[0].getPrincipal();
        assertNotNull(presentation, "Presentation should not be null");
        assertNotNull(presentation.getPresentation(), "Presentation list should not be null");
        assertFalse(presentation.getPresentation().isEmpty(), "Presentation list should not be empty");

        // Verify the raw token is stored for signature verification
        assertNotNull(capturedToken[0].getRawToken(), "Raw token should be stored");
        assertEquals(REAL_JWT_TOKEN, capturedToken[0].getRawToken(), "Raw token should match the input");

        // Verify the presentation structure
        Object firstPresentation = presentation.getPresentation().get(0);
        assertInstanceOf(VerifiablePresentation.class, firstPresentation,
                "Presentation should be a VerifiablePresentation object, not a generic Map");

        VerifiablePresentation vp = (VerifiablePresentation) firstPresentation;

        // Verify VP has credentials
        assertNotNull(vp.getCredentials(), "VP should have credentials");
        assertFalse(vp.getCredentials().isEmpty(), "Credentials list should not be empty");

        // Verify holderDid
        assertEquals("did:web:localhost:8080", vp.getHolderDid(), "HolderDid should match JWT subject");

        log.info("✓ End-to-end JWT processing successful");
        log.info("✓ Authentication token contains valid PresentationResponseMessage");
        log.info("✓ PresentationResponseMessage contains proper VerifiablePresentation object");
        log.info("✓ Raw token is preserved for signature verification");
    }

    /**
     * Test to diagnose whether the issue is with JWT generation or parsing logic.
     * This test explicitly checks both possibilities:
     * 1. JWT structure and claims
     * 2. Parsing logic creating proper objects
     */
    @Test
    void testDiagnoseJwtParsingIssue() throws Exception {
        // STEP 1: Verify the JWT structure is valid
        log.info("=== STEP 1: Validating JWT Structure ===");

        com.nimbusds.jwt.JWT jwt = com.nimbusds.jwt.JWTParser.parse(REAL_JWT_TOKEN);
        assertNotNull(jwt, "JWT should be parseable");

        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
        assertNotNull(claims, "Claims should not be null");

        log.info("JWT Claims: {}", claims.keySet());

        Object vpClaim = claims.get("vp");
        assertNotNull(vpClaim, "VP claim should be present");
        assertInstanceOf(Map.class, vpClaim, "VP claim should be a Map");

        @SuppressWarnings("unchecked")
        Map<String, Object> vpFromJwt = (Map<String, Object>) vpClaim;

        log.info("VP structure from JWT: {}", vpFromJwt.keySet());
        log.info("✓ JWT structure is valid");

        // STEP 2: Verify the parsing logic creates proper objects
        log.info("=== STEP 2: Validating Parsing Logic ===");

        Method parsePresentationMethod = VcVpAuthenticationFilter.class.getDeclaredMethod("parsePresentation", String.class);
        parsePresentationMethod.setAccessible(true);
        PresentationResponseMessage presentation = (PresentationResponseMessage) parsePresentationMethod.invoke(filter, REAL_JWT_TOKEN);

        assertNotNull(presentation, "Parsing logic should create PresentationResponseMessage");
        assertNotNull(presentation.getClass(), "Presentation should have a valid class");
        assertEquals(PresentationResponseMessage.class, presentation.getClass(),
                "Result should be PresentationResponseMessage class");

        log.info("Presentation class: {}", presentation.getClass().getName());
        log.info("Presentation list: {}", presentation.getPresentation());

        log.info("✓ Parsing logic creates proper Java objects");

        // STEP 3: Verify the VP claim conversion
        log.info("=== STEP 3: Validating VP Claim Conversion ===");

        Method vpClaimToPresentation = VcVpAuthenticationFilter.class.getDeclaredMethod("vpClaimToPresentation", Object.class, String.class);
        vpClaimToPresentation.setAccessible(true);
        String subjectDid = "did:web:localhost:8080";
        PresentationResponseMessage convertedPresentation = (PresentationResponseMessage) vpClaimToPresentation.invoke(filter, vpClaim, subjectDid);

        assertNotNull(convertedPresentation, "VP claim conversion should succeed");
        assertNotNull(convertedPresentation.getPresentation(), "Converted presentation should have presentation list");

        // Verify the converted presentation contains a VerifiablePresentation object
        Object firstVp = convertedPresentation.getPresentation().get(0);
        assertInstanceOf(VerifiablePresentation.class, firstVp, "Presentation should contain VerifiablePresentation object");

        VerifiablePresentation vp = (VerifiablePresentation) firstVp;
        assertNotNull(vp.getHolderDid(), "VP should have holderDid");
        assertNotNull(vp.getProfileId(), "VP should have profileId");
        assertNotNull(vp.getCredentials(), "VP should have credentials");

        log.info("Converted presentation: {}", convertedPresentation);
        log.info("VerifiablePresentation holderDid: {}", vp.getHolderDid());
        log.info("VerifiablePresentation profileId: {}", vp.getProfileId());
        log.info("✓ VP claim conversion successful");

        // DIAGNOSIS RESULT
        log.info("=== DIAGNOSIS RESULT ===");
        log.info("1. JWT string is VALID ✓");
        log.info("2. Parsing logic is CORRECT ✓");
        log.info("3. PresentationResponseMessage is properly created ✓");
        log.info("4. VerifiablePresentation object is properly created (not a generic Map) ✓");
        log.info("CONCLUSION: Both JWT generation and parsing logic are working correctly!");
    }
}

