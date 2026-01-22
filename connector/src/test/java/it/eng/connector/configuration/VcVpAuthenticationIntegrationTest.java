package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.model.ValidationError;
import it.eng.dcp.model.ValidationReport;
import it.eng.dcp.model.VerifiablePresentation;
import it.eng.dcp.service.PresentationValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Integration tests for VC/VP authentication flow.
 */
@ExtendWith(MockitoExtension.class)
class VcVpAuthenticationIntegrationTest {

    @Mock
    private PresentationValidationService presentationValidationService;
    @Mock
    private DidResolverService didResolverService;

    private VcVpAuthenticationProvider authenticationProvider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        authenticationProvider = new VcVpAuthenticationProvider(presentationValidationService, didResolverService);
        // Enable VC/VP authentication for tests
        org.springframework.test.util.ReflectionTestUtils.setField(authenticationProvider, "vcVpEnabled", true);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testSuccessfulAuthentication() {
        // Given a valid presentation with proper VerifiablePresentation object
        java.util.Map<String, Object> credential = new java.util.HashMap<>();
        credential.put("type", "MembershipCredential");
        credential.put("format", "json");
        credential.put("credentialSubject", java.util.Map.of(
            "id", "did:example:holder123",
            "membershipType", "Premium",
            "status", "Active"
        ));

        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder123")
                .profileId(ProfileId.VC20_BSSL_JWT)
                .credentialIds(List.of("urn:uuid:cred1"))
                .credentials(List.of(credential))
                .build();

        PresentationResponseMessage presentation = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(vp))
                .build();

        VcVpAuthenticationToken token = new VcVpAuthenticationToken(presentation, null);

        // Mock validation to return success
        ValidationReport successReport = ValidationReport.Builder.newInstance().build();
        when(presentationValidationService.validate(any(PresentationResponseMessage.class), anyList(), any()))
                .thenReturn(successReport);

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then
        assertNotNull(result);
        assertTrue(result.isAuthenticated());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_CONNECTOR")));
    }

    @Test
    void testFailedAuthentication_InvalidPresentation() {
        // Given an invalid presentation with proper VerifiablePresentation object
        java.util.Map<String, Object> credential = new java.util.HashMap<>();
        credential.put("type", "MembershipCredential");
        credential.put("format", "json");
        credential.put("credentialSubject", java.util.Map.of(
            "id", "did:example:holder123",
            "membershipType", "Premium"
        ));

        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder123")
                .profileId(ProfileId.VC20_BSSL_JWT)
                .credentialIds(List.of("urn:uuid:cred1"))
                .credentials(List.of(credential))
                .build();

        PresentationResponseMessage presentation = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(vp))
                .build();

        VcVpAuthenticationToken token = new VcVpAuthenticationToken(presentation, null);

        // Mock validation to return errors
        ValidationReport failureReport = ValidationReport.Builder.newInstance().build();
        failureReport.addError(new ValidationError("VC_EXPIRED", "Credential expired", ValidationError.Severity.ERROR));
        org.mockito.Mockito.lenient()
                .when(presentationValidationService.validate(any(PresentationResponseMessage.class), anyList(), any()))
                .thenReturn(failureReport);

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then - Should return null to allow fallback, not throw exception
        assertNull(result);
    }

    @Test
    void testFailedAuthentication_NullPresentation() {
        // Given a null presentation
        VcVpAuthenticationToken token = new VcVpAuthenticationToken(null, null);

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then - Should return null to allow fallback
        assertNull(result);
    }

    @Test
    void testFilterParsesDirectJson() throws Exception {
        // Given a direct JSON presentation with proper VerifiablePresentation
        java.util.Map<String, Object> credential = new java.util.HashMap<>();
        credential.put("type", "MembershipCredential");
        credential.put("format", "json");
        credential.put("credentialSubject", java.util.Map.of(
            "id", "did:example:holder123",
            "membershipType", "Premium"
        ));

        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder123")
                .profileId(ProfileId.VC20_BSSL_JWT)
                .credentialIds(List.of("urn:uuid:cred1"))
                .credentials(List.of(credential))
                .build();

        PresentationResponseMessage presentation = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(vp))
                .build();

        String json = objectMapper.writeValueAsString(presentation);

        // When - simulating filter parsing
        PresentationResponseMessage parsed = objectMapper.readValue(json, PresentationResponseMessage.class);

        // Then
        assertNotNull(parsed);
        assertNotNull(parsed.getPresentation());
        assertFalse(parsed.getPresentation().isEmpty());
    }

    @Test
    void testFilterParsesBase64EncodedJson() throws Exception {
        // Given a base64-encoded JSON presentation with proper VerifiablePresentation
        java.util.Map<String, Object> credential = new java.util.HashMap<>();
        credential.put("type", "MembershipCredential");
        credential.put("format", "json");
        credential.put("credentialSubject", java.util.Map.of(
            "id", "did:example:holder123",
            "membershipType", "Premium"
        ));

        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder123")
                .profileId(ProfileId.VC20_BSSL_JWT)
                .credentialIds(List.of("urn:uuid:cred1"))
                .credentials(List.of(credential))
                .build();

        PresentationResponseMessage presentation = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(vp))
                .build();

        String json = objectMapper.writeValueAsString(presentation);
        String base64 = Base64.getEncoder().encodeToString(json.getBytes());

        // When - simulating filter parsing
        byte[] decoded = Base64.getDecoder().decode(base64);
        String decodedJson = new String(decoded);
        PresentationResponseMessage parsed = objectMapper.readValue(decodedJson, PresentationResponseMessage.class);

        // Then
        assertNotNull(parsed);
        assertNotNull(parsed.getPresentation());
        assertFalse(parsed.getPresentation().isEmpty());
    }

    @Test
    void testExtractSubjectFromPresentation() {
        // Given a presentation with proper VerifiablePresentation object
        java.util.Map<String, Object> credential = new java.util.HashMap<>();
        credential.put("type", "MembershipCredential");
        credential.put("format", "json");
        credential.put("credentialSubject", java.util.Map.of(
            "id", "did:example:holder123",
            "membershipType", "Premium"
        ));

        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder123")
                .profileId(ProfileId.VC20_BSSL_JWT)
                .credentialIds(List.of("urn:uuid:cred1"))
                .credentials(List.of(credential))
                .build();

        PresentationResponseMessage presentation = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(vp))
                .build();

        VcVpAuthenticationToken token = new VcVpAuthenticationToken(presentation, null);

        // Mock validation to return success
        ValidationReport successReport = ValidationReport.Builder.newInstance().build();
        when(presentationValidationService.validate(any(PresentationResponseMessage.class), anyList(), any()))
                .thenReturn(successReport);

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then - subject extracted from VerifiablePresentation.holderDid
        assertNotNull(result);
        assertNotNull(result.getName());
        assertEquals("did:example:holder123", result.getName());
    }

    @Test
    void testSupportsVcVpAuthenticationToken() {
        // When/Then
        assertTrue(authenticationProvider.supports(VcVpAuthenticationToken.class));
    }
}

