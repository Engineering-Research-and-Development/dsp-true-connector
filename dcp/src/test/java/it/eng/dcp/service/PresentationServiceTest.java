package it.eng.dcp.service;

import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.model.*;
import it.eng.dcp.repository.VerifiableCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PresentationServiceTest {

    @Mock
    private VerifiableCredentialRepository credentialRepository;

    @Mock
    private VerifiablePresentationSigner vpSigner;

    private PresentationService presentationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        presentationService = new PresentationService(credentialRepository, vpSigner);
    }

    @Test
    void testCreatePresentation_JwtFormat() {
        // Arrange
        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("MembershipCredential"))
                .build();

        VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-123")
                .credentialType("MembershipCredential")
                .holderDid("did:web:localhost:8080")
                .profileId(ProfileId.VC11_SL2021_JWT.toString())
                .build();

        when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
        when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.signature");

        // Act
        PresentationResponseMessage response = presentationService.createPresentation(query);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getPresentation());
        assertEquals(1, response.getPresentation().size());

        Object presentation = response.getPresentation().get(0);
        assertTrue(presentation instanceof String, "Presentation should be a JWT string");
        String jwtString = (String) presentation;
        assertTrue(jwtString.contains("."), "JWT should contain dots separating header, payload, signature");

        System.out.println("JWT Presentation: " + jwtString);

        // Verify that vpSigner.sign was called with "jwt" format
        verify(vpSigner).sign(any(VerifiablePresentation.class), eq("jwt"));
    }

    @Test
    void testCreatePresentation_JsonLdFormat() {
        // Arrange
        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("MembershipCredential"))
                .build();

        VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-123")
                .credentialType("MembershipCredential")
                .holderDid("did:web:localhost:8080")
                .profileId(ProfileId.VC11_SL2021_JSONLD.toString())
                .build();

        when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode jsonLdPresentation = mapper.createObjectNode();
        jsonLdPresentation.put("id", "urn:uuid:vp-123");
        jsonLdPresentation.put("type", "VerifiablePresentation");

        when(vpSigner.sign(any(VerifiablePresentation.class), eq("json-ld")))
                .thenReturn(jsonLdPresentation);

        // Act
        PresentationResponseMessage response = presentationService.createPresentation(query);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getPresentation());
        assertEquals(1, response.getPresentation().size());

        Object presentation = response.getPresentation().get(0);
        assertTrue(presentation instanceof com.fasterxml.jackson.databind.node.ObjectNode,
                   "Presentation should be a JSON-LD object");

        // Verify that vpSigner.sign was called with "json-ld" format
        verify(vpSigner).sign(any(VerifiablePresentation.class), eq("json-ld"));
    }

    @Test
    void testCreatePresentation_PresentationDefinitionFormat_Jwt() {
        // Arrange - Credential has JWT profile, query has presentationDefinition requesting JWT format
        Map<String, Object> presentationDef = new java.util.HashMap<>();
        presentationDef.put("id", "test-def");
        Map<String, Object> format = new java.util.HashMap<>();
        format.put("jwt_vp", new java.util.HashMap<>()); // JWT VP format
        presentationDef.put("format", format);

        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("MembershipCredential"))
                .presentationDefinition(presentationDef)
                .build();

        VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-123")
                .credentialType("MembershipCredential")
                .holderDid("did:web:localhost:8080")
                .profileId(ProfileId.VC11_SL2021_JWT.toString())
                .build();

        when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
        when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.signature");

        // Act
        PresentationResponseMessage response = presentationService.createPresentation(query);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getPresentation().size());
        assertTrue(response.getPresentation().get(0) instanceof String,
                   "Should return JWT based on presentationDefinition format");

        // Verify that vpSigner.sign was called with "jwt" format
        verify(vpSigner).sign(any(VerifiablePresentation.class), eq("jwt"));
    }

    @Test
    void testCreatePresentation_PresentationDefinitionFormat_JsonLd() {
        // Arrange - Query has presentationDefinition requesting JSON-LD format
        Map<String, Object> presentationDef = new java.util.HashMap<>();
        presentationDef.put("id", "test-def");
        Map<String, Object> format = new java.util.HashMap<>();
        format.put("ldp_vp", new java.util.HashMap<>()); // JSON-LD VP format
        presentationDef.put("format", format);

        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("MembershipCredential"))
                .presentationDefinition(presentationDef)
                .build();

        VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-123")
                .credentialType("MembershipCredential")
                .holderDid("did:web:localhost:8080")
                .profileId(ProfileId.VC11_SL2021_JWT.toString()) // JWT profile but override with definition
                .build();

        when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode jsonLdPresentation = mapper.createObjectNode();
        jsonLdPresentation.put("id", "urn:uuid:vp-123");

        when(vpSigner.sign(any(VerifiablePresentation.class), eq("json-ld")))
                .thenReturn(jsonLdPresentation);

        // Act
        PresentationResponseMessage response = presentationService.createPresentation(query);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getPresentation().size());
        assertTrue(response.getPresentation().get(0) instanceof com.fasterxml.jackson.databind.node.ObjectNode,
                   "Should return JSON-LD based on presentationDefinition format");

        // Verify that vpSigner.sign was called with "json-ld" format
        verify(vpSigner).sign(any(VerifiablePresentation.class), eq("json-ld"));
    }
}

