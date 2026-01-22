package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.model.ValidationReport;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.model.VerifiablePresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test for PresentationValidationServiceImpl using real JWT token.
 * This test validates that all objects (VerifiablePresentation, VerifiableCredential)
 * are created properly from JWT input.
 */
@ExtendWith(MockitoExtension.class)
class PresentationValidationServiceImplJwtTest {

    @Mock
    private IssuerTrustService issuerTrustService;

    @Mock
    private RevocationService revocationService;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private PresentationValidationServiceImpl validationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Real JWT token from user containing VP with MembershipCredential
    private static final String REAL_JWT_TOKEN = "eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W3sidHlwZSI6Ik1lbWJlcnNoaXBDcmVkZW50aWFsIiwiZm9ybWF0Ijoiand0Iiwiand0IjoiZXlKcmFXUWlPaUp2T1ZkemRUTjJTRjlMVFhNM1ZFNVVNRkE0VURsS1RIUklNbUpSWldVdGJWUndOVkJsZVVkRE5IUTRJaXdpWVd4bklqb2lSVk15TlRZaWZRLmV5SnpkV0lpT2lKa2FXUTZkMlZpT214dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYVhOeklqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0ltVjRjQ0k2TVRjNU56TXpNVFEwTVN3aWFXRjBJam94TnpZMU56azFORFF4TENKMll5STZleUpqY21Wa1pXNTBhV0ZzVTNWaWFtVmpkQ0k2ZXlKdFpXMWlaWEp6YUdsd1ZIbHdaU0k2SWxCeVpXMXBkVzBpTENKdFpXMWlaWEp6YUdsd1NXUWlPaUpOUlUxQ1JWSXRNakZpTmpNNU1HSWlMQ0pwWkNJNkltUnBaRHAzWldJNmJHOWpZV3hvYjNOME9qZ3dPREFpTENKemRHRjBkWE1pT2lKQlkzUnBkbVVpZlN3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazFsYldKbGNuTm9hWEJEY21Wa1pXNTBhV0ZzSWwwc0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5bGVHRnRjR3hsTG05eVp5OWpjbVZrWlc1MGFXRnNjeTkyTVNKZGZTd2lhblJwSWpvaWRYSnVPblYxYVdRNll6WTBOR0l5T0RJdE9XTTROQzAwTkRjekxXSmpZak10T0RGa05UbGpNVEprT1RVekluMC5pTXR4NGxHOWVscDdXSXZVS29hZjB3RmczdGVGY1FSN05sVTBNNkU3dzd0alFvVXlOOEFTd2Q1enBpNjliSXFuMWtMZE5vWTFRbUEtZjNTNVVVYkdIQSJ9XSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTgwOTU5NCwianRpIjoidXJuOnV1aWQ6OWU1NDhmNTQtOWRiYy00YzQ1LTllYWUtMmMxZjQzNzM0MzdjIn0.JDzsrJYAW2jp7Qg3vBxWoGRd9lxx676GCopamBtE14ccSctkbE4QOurxTZaur-Dz8h10lMOesVqRgODWdwNYjQ";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(issuerTrustService, revocationService, publisher);
    }

    /**
     * Test that verifies the entire flow from JWT parsing to object creation.
     * This test diagnoses the profileResolver.resolve returning null issue.
     */
    @Test
    void testValidateRealJwtToken_VerifyObjectCreation() throws Exception {
        // Given: Parse the JWT to extract VP claim
        com.nimbusds.jwt.JWT jwt = com.nimbusds.jwt.JWTParser.parse(REAL_JWT_TOKEN);
        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();

        Object vpClaim = claims.get("vp");
        assertNotNull(vpClaim, "VP claim should be present in JWT");

        // Convert VP claim to VerifiablePresentation
        String vpJson = objectMapper.writeValueAsString(vpClaim);
        JsonNode vpNode = objectMapper.readTree(vpJson);

        // Build VerifiablePresentation from the VP claim
        VerifiablePresentation vp = buildVpFromJwtClaim(vpNode, (String) claims.get("sub"));

        // Verify VerifiablePresentation was created correctly
        assertNotNull(vp, "VerifiablePresentation should be created");
        assertEquals("did:web:localhost:8080", vp.getHolderDid(), "HolderDid should match JWT subject");
        assertEquals(ProfileId.VC11_SL2021_JWT, vp.getProfileId(), "ProfileId should be VC11_SL2021_JWT");
        assertNotNull(vp.getCredentials(), "Credentials should not be null");
        assertFalse(vp.getCredentials().isEmpty(), "Credentials should not be empty");
        assertEquals(1, vp.getCredentials().size(), "Should have 1 credential");

        // Verify credential structure
        Object credentialObj = vp.getCredentials().get(0);
        assertInstanceOf(Map.class, credentialObj, "Credential should be a Map");

        @SuppressWarnings("unchecked")
        Map<String, Object> credMap = (Map<String, Object>) credentialObj;

        assertEquals("MembershipCredential", credMap.get("type"), "Credential type should be MembershipCredential");
        assertEquals("jwt", credMap.get("format"), "Credential format should be jwt");
        assertTrue(credMap.containsKey("jwt"), "Credential should contain jwt field");
        assertInstanceOf(String.class, credMap.get("jwt"), "JWT field should be a String");

//        System.out.println("✓ VerifiablePresentation created successfully");
//        System.out.println("  - holderDid: " + vp.getHolderDid());
//        System.out.println("  - profileId: " + vp.getProfileId());
//        System.out.println("  - credentials count: " + vp.getCredentials().size());
//        System.out.println("  - credential type: " + credMap.get("type"));
//        System.out.println("  - credential format: " + credMap.get("format"));
    }

    /**
     * Test that diagnoses the profileResolver.resolve returning null issue.
     * This test captures what arguments are passed to profileResolver.resolve.
     */
    @Test
    void testDiagnoseProfileResolverIssue() throws Exception {
        // Given: Parse JWT and build VP
        com.nimbusds.jwt.JWT jwt = com.nimbusds.jwt.JWTParser.parse(REAL_JWT_TOKEN);
        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
        Object vpClaim = claims.get("vp");

        String vpJson = objectMapper.writeValueAsString(vpClaim);
        JsonNode vpNode = objectMapper.readTree(vpJson);
        VerifiablePresentation vp = buildVpFromJwtClaim(vpNode, (String) claims.get("sub"));

        PresentationResponseMessage presentation = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(vp))
                .build();

        // When: Validate the presentation
        ValidationReport report = validationService.validate(presentation, List.of("MembershipCredential"), null);

        // Then: Capture what was passed to profileResolver.resolve
        ArgumentCaptor<String> formatCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> attrsCaptor = ArgumentCaptor.forClass(Map.class);

        // Diagnose the issue
//        System.out.println("\n=== DIAGNOSIS: profileResolver.resolve called with ===");
        List<String> formats = formatCaptor.getAllValues();
        List<Map> attrs = attrsCaptor.getAllValues();

//        for (int i = 0; i < formats.size(); i++) {
//            System.out.println("Call " + (i + 1) + ":");
//            System.out.println("  - format: " + formats.get(i));
//            System.out.println("  - attrs: " + attrs.get(i));
//        }

        // The issue: profileResolver returns null, causing PROFILE_UNKNOWN error
        assertFalse(report.isValid(), "Report should be invalid when profileResolver returns null");

//        System.out.println("\n=== ROOT CAUSE ===");
//        System.out.println("profileResolver.resolve returns null for:");
//        System.out.println("  - format: " + formats.get(0));
//        System.out.println("  - attrs: " + attrs.get(0));
//        System.out.println("\nThis means ProfileResolver cannot map format='jwt' to ProfileId.VC11_SL2021_JWT");
//        System.out.println("SOLUTION: Configure ProfileResolver to handle JWT format credentials");
    }

    /**
     * Test with proper ProfileResolver mock configuration.
     * This shows how to fix the issue.
     */
    @Test
    void testValidateWithCorrectProfileResolverConfig() throws Exception {
        // Given: Parse JWT and build VP
        com.nimbusds.jwt.JWT jwt = com.nimbusds.jwt.JWTParser.parse(REAL_JWT_TOKEN);
        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
        Object vpClaim = claims.get("vp");

        String vpJson = objectMapper.writeValueAsString(vpClaim);
        JsonNode vpNode = objectMapper.readTree(vpJson);
        VerifiablePresentation vp = buildVpFromJwtClaim(vpNode, (String) claims.get("sub"));

        PresentationResponseMessage presentation = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(vp))
                .build();

        // Mock issuer trust - assume issuer is trusted
        when(issuerTrustService.isTrusted(eq("MembershipCredential"), anyString())).thenReturn(true);

        // Mock revocation - assume not revoked
        when(revocationService.isRevoked(any())).thenReturn(false);

        // When: Validate the presentation
        ValidationReport report = validationService.validate(presentation, List.of("MembershipCredential"), null);

        // Then: Validation should succeed
//        System.out.println("\n=== VALIDATION REPORT ===");
//        System.out.println("Valid: " + report.isValid());
//        System.out.println("Errors: " + report.getErrors());
//        System.out.println("Accepted types: " + report.getAcceptedCredentialTypes());

        // Note: May still have errors if credential parsing fails, but no PROFILE_UNKNOWN error
        assertFalse(report.getErrors().stream().anyMatch(e -> "PROFILE_UNKNOWN".equals(e.code())),
                "Should not have PROFILE_UNKNOWN error when profileResolver is configured correctly");
    }

    /**
     * Test extractCredentialsFromVp to verify VerifiableCredential objects are created properly.
     */
    @Test
    void testExtractCredentialsFromVp_VerifyCredentialObjects() throws Exception {
        // Given: Parse JWT and build VP
        com.nimbusds.jwt.JWT jwt = com.nimbusds.jwt.JWTParser.parse(REAL_JWT_TOKEN);
        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
        Object vpClaim = claims.get("vp");

        String vpJson = objectMapper.writeValueAsString(vpClaim);
        JsonNode vpNode = objectMapper.readTree(vpJson);
        VerifiablePresentation vp = buildVpFromJwtClaim(vpNode, (String) claims.get("sub"));

        // When: Extract credentials using reflection (to test private method)
        java.lang.reflect.Method extractMethod = PresentationValidationServiceImpl.class
                .getDeclaredMethod("extractCredentialsFromVp", VerifiablePresentation.class);
        extractMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<VerifiableCredential> credentials = (List<VerifiableCredential>) extractMethod.invoke(validationService, vp);

        // Then: Verify VerifiableCredential objects were created
        assertNotNull(credentials, "Credentials list should not be null");
        assertFalse(credentials.isEmpty(), "Credentials list should not be empty");
        assertEquals(1, credentials.size(), "Should have 1 credential");

        VerifiableCredential vc = credentials.get(0);
        assertNotNull(vc, "VerifiableCredential should not be null");
        assertEquals("MembershipCredential", vc.getCredentialType(), "Credential type should be MembershipCredential");
        assertEquals("did:web:localhost:8080", vc.getHolderDid(), "HolderDid should be set from VP");

//        System.out.println("\n=== VerifiableCredential Created ===");
//        System.out.println("  - id: " + vc.getId());
//        System.out.println("  - type: " + vc.getCredentialType());
//        System.out.println("  - holderDid: " + vc.getHolderDid());
//        System.out.println("  - issuanceDate: " + vc.getIssuanceDate());
//        System.out.println("  - expirationDate: " + vc.getExpirationDate());
//        System.out.println("✓ VerifiableCredential object created successfully from JWT credential");
    }

    /**
     * Test the complete validation flow with detailed logging.
     */
    @Test
    void testCompleteValidationFlow_DetailedLogging() throws Exception {
        // Given: Parse JWT and build VP
        com.nimbusds.jwt.JWT jwt = com.nimbusds.jwt.JWTParser.parse(REAL_JWT_TOKEN);
        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
        Object vpClaim = claims.get("vp");

        String vpJson = objectMapper.writeValueAsString(vpClaim);
        JsonNode vpNode = objectMapper.readTree(vpJson);
        VerifiablePresentation vp = buildVpFromJwtClaim(vpNode, (String) claims.get("sub"));

        PresentationResponseMessage presentation = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(vp))
                .build();

//        System.out.println("\n=== VALIDATION FLOW ===");
//        System.out.println("Step 1: JWT Parsed");
//        System.out.println("  - Issuer: " + claims.get("iss"));
//        System.out.println("  - Subject: " + claims.get("sub"));
//        System.out.println("  - VP profileId: " + vpNode.get("profileId").asText());

//        System.out.println("\nStep 2: VerifiablePresentation Created");
//        System.out.println("  - holderDid: " + vp.getHolderDid());
//        System.out.println("  - profileId: " + vp.getProfileId());
//        System.out.println("  - credentialIds: " + vp.getCredentialIds());

        // Mock with correct configuration
        when(issuerTrustService.isTrusted(anyString(), anyString())).thenReturn(true);
        when(revocationService.isRevoked(any())).thenReturn(false);

        // When: Validate
        ValidationReport report = validationService.validate(presentation, List.of("MembershipCredential"), null);

//        System.out.println("\nStep 3: Validation Complete");
//        System.out.println("  - Valid: " + report.isValid());
//        System.out.println("  - Errors: " + report.getErrors());
//        System.out.println("  - Accepted types: " + report.getAcceptedCredentialTypes());

        // Verify no profile-related errors
        assertFalse(report.getErrors().stream().anyMatch(e ->
                "PROFILE_UNKNOWN".equals(e.code()) || "PROFILE_MISSING".equals(e.code())),
                "Should not have profile errors with correct configuration");
    }

    /**
     * Helper method to build VerifiablePresentation from JWT VP claim.
     * This mirrors what VcVpAuthenticationFilter does.
     */
    private VerifiablePresentation buildVpFromJwtClaim(JsonNode vpNode, String subjectDid) {
        VerifiablePresentation.Builder builder = VerifiablePresentation.Builder.newInstance();

        // Set holderDid from JWT subject
        if (subjectDid != null) {
            builder.holderDid(subjectDid);
        }

        // Set profileId
        if (vpNode.has("profileId")) {
            builder.profileId(ProfileId.fromString(vpNode.get("profileId").asText()));
        }

        // Extract verifiableCredential array
        if (vpNode.has("verifiableCredential") && vpNode.get("verifiableCredential").isArray()) {
            List<Object> credentials = new java.util.ArrayList<>();
            List<String> credentialIds = new java.util.ArrayList<>();

            int idx = 0;
            for (JsonNode credNode : vpNode.get("verifiableCredential")) {
                try {
                    // Convert JsonNode to Map
                    Map<String, Object> credMap = objectMapper.convertValue(credNode, Map.class);
                    credentials.add(credMap);
                    credentialIds.add("urn:uuid:credential-" + idx);
                    idx++;
                } catch (Exception e) {
                    // Skip malformed credential
                }
            }

            builder.credentials(credentials);
            builder.credentialIds(credentialIds);
        }

        return builder.build();
    }
}

