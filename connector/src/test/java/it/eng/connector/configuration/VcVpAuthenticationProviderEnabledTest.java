package it.eng.connector.configuration;

import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.model.PresentationResponseMessage;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Tests for VcVpAuthenticationProvider behavior with dcp.vp.enabled property.
 */
@ExtendWith(MockitoExtension.class)
class VcVpAuthenticationProviderEnabledTest {

    @Mock
    private PresentationValidationService presentationValidationService;
    @Mock
    private DidResolverService didResolverService;

    private VcVpAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        authenticationProvider = new VcVpAuthenticationProvider(presentationValidationService, didResolverService);
    }

    @Test
    void testAuthenticationWithVcVpEnabled() {
        // Given: VC/VP is enabled
        ReflectionTestUtils.setField(authenticationProvider, "vcVpEnabled", true);

        // Create a proper VerifiablePresentation with credentials
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
        when(presentationValidationService.validate(any(), anyList(), any()))
                .thenReturn(successReport);

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then
        assertNotNull(result, "Should return authenticated token when VC/VP is enabled and valid");
        assertTrue(result.isAuthenticated(), "Token should be authenticated");
        verify(presentationValidationService).validate(any(), anyList(), any());
    }

    @Test
    void testAuthenticationWithVcVpDisabled() {
        // Given: VC/VP is disabled
        ReflectionTestUtils.setField(authenticationProvider, "vcVpEnabled", false);

        // Create a proper VerifiablePresentation with credentials
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

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then
        assertNull(result, "Should return null when VC/VP is disabled to allow fallback");
        verify(presentationValidationService, never()).validate(any(), anyList(), any());
    }

    @Test
    void testAuthenticationWithInvalidPresentation() {
        // Given: VC/VP is enabled but presentation is invalid
        ReflectionTestUtils.setField(authenticationProvider, "vcVpEnabled", true);

        // Create a proper VerifiablePresentation with credentials
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
        when(presentationValidationService.validate(any(), anyList(), any()))
                .thenReturn(failureReport);

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then
        assertNull(result, "Should return null when presentation validation fails to allow fallback");
        verify(presentationValidationService).validate(any(), anyList(), any());
    }

    @Test
    void testAuthenticationWithNullPresentation() {
        // Given: VC/VP is enabled but presentation is null
        ReflectionTestUtils.setField(authenticationProvider, "vcVpEnabled", true);

        VcVpAuthenticationToken token = new VcVpAuthenticationToken(null, null);

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then
        assertNull(result, "Should return null when presentation is null to allow fallback");
        verify(presentationValidationService, never()).validate(any(), anyList(), any());
    }

    @Test
    void testAuthenticationWithValidationException() {
        // Given: VC/VP is enabled but validation throws exception
        ReflectionTestUtils.setField(authenticationProvider, "vcVpEnabled", true);

        // Create a proper VerifiablePresentation with credentials
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

        // Mock validation to throw exception
        when(presentationValidationService.validate(any(), anyList(), any()))
                .thenThrow(new RuntimeException("Validation service error"));

        // When
        Authentication result = authenticationProvider.authenticate(token);

        // Then
        assertNull(result, "Should return null when validation throws exception to allow fallback");
        verify(presentationValidationService).validate(any(), anyList(), any());
    }

    @Test
    void testSupportsVcVpAuthenticationToken() {
        // When/Then
        assertTrue(authenticationProvider.supports(VcVpAuthenticationToken.class),
                "Should support VcVpAuthenticationToken regardless of enabled state");
    }

    @Test
    void testDefaultVcVpEnabledIsFalse() {
        // Given: Fresh provider without setting vcVpEnabled
        VcVpAuthenticationProvider defaultProvider = new VcVpAuthenticationProvider(presentationValidationService, didResolverService);

        // Create a proper VerifiablePresentation with credentials
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

        // When
        Authentication result = defaultProvider.authenticate(token);

        // Then
        assertNull(result, "Default value of vcVpEnabled should be false, returning null for fallback");
        verify(presentationValidationService, never()).validate(any(), anyList(), any());
    }
}

