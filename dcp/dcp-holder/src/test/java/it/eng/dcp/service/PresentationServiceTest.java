package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.holder.model.VerifiableCredential;
import it.eng.dcp.holder.model.VerifiablePresentation;
import it.eng.dcp.holder.repository.VerifiableCredentialRepository;
import it.eng.dcp.holder.service.PresentationService;
import it.eng.dcp.holder.service.VerifiablePresentationSigner;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("did:web:localhost:8080")
                .build();

        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("MembershipCredential"))
                .build();

        VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-123")
                .credentialType("MembershipCredential")
                .holderDid("did:web:localhost:8080")
                .profileId(ProfileId.VC11_SL2021_JWT)
                .build();

        when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
        when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.signature");

        // Act
        PresentationResponseMessage response = presentationService.createPresentation(query, claims);

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
    void testCreatePresentation_PresentationDefinitionFormat_Jwt() {
        // Arrange - Credential has JWT profile, query has presentationDefinition requesting JWT format
        Map<String, Object> presentationDef = new java.util.HashMap<>();
        presentationDef.put("id", "test-def");
        Map<String, Object> format = new java.util.HashMap<>();
        format.put("jwt_vp", new java.util.HashMap<>()); // JWT VP format
        presentationDef.put("format", format);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("did:web:localhost:8080")
                .build();

        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("MembershipCredential"))
                .presentationDefinition(presentationDef)
                .build();

        VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-123")
                .credentialType("MembershipCredential")
                .holderDid("did:web:localhost:8080")
                .profileId(ProfileId.VC11_SL2021_JWT)
                .build();

        when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
        when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.signature");

        // Act
        PresentationResponseMessage response = presentationService.createPresentation(query, claims);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getPresentation().size());
        assertTrue(response.getPresentation().get(0) instanceof String,
                   "Should return JWT based on presentationDefinition format");

        // Verify that vpSigner.sign was called with "jwt" format
        verify(vpSigner).sign(any(VerifiablePresentation.class), eq("jwt"));
    }

    @Test
    @Disabled("JSON-LD VP format not yet supported in VP signer")
    void testCreatePresentation_PresentationDefinitionFormat_JsonLd() {
        // Arrange - Query has presentationDefinition requesting JSON-LD format
        Map<String, Object> presentationDef = new java.util.HashMap<>();
        presentationDef.put("id", "test-def");
        Map<String, Object> format = new java.util.HashMap<>();
        format.put("ldp_vp", new java.util.HashMap<>()); // JSON-LD VP format
        presentationDef.put("format", format);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("did:web:localhost:8080")
                .build();

        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("MembershipCredential"))
                .presentationDefinition(presentationDef)
                .build();

        VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-123")
                .credentialType("MembershipCredential")
                .holderDid("did:web:localhost:8080")
                .profileId(ProfileId.VC11_SL2021_JWT) // JWT profile but override with definition
                .build();

        when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonLdPresentation = mapper.createObjectNode();
        jsonLdPresentation.put("id", "urn:uuid:vp-123");

        when(vpSigner.sign(any(VerifiablePresentation.class), eq("json-ld")))
                .thenReturn(jsonLdPresentation);

        // Act
        PresentationResponseMessage response = presentationService.createPresentation(query, claims);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getPresentation().size());
        assertTrue(response.getPresentation().get(0) instanceof com.fasterxml.jackson.databind.node.ObjectNode,
                   "Should return JSON-LD based on presentationDefinition format");

        // Verify that vpSigner.sign was called with "json-ld" format
        verify(vpSigner).sign(any(VerifiablePresentation.class), eq("json-ld"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SCOPE ENFORCEMENT TESTS (DCP Protocol v1.0 Section 5.1)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scope Enforcement Tests")
    @SuppressWarnings("unchecked")
    class ScopeEnforcementTests {

        @Test
        @DisplayName("Should return only credentials matching authorized scopes (intersection)")
        void shouldEnforceScopesFromAccessToken() {
            // Given: Access token authorizes only MembershipCredential
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of("MembershipCredential"))
                    .build();

            // And: Query requests both MembershipCredential and Iso27001Credential
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("MembershipCredential", "Iso27001Credential"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should only fetch MembershipCredential (the intersection)
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedScopes = scopeCaptor.getValue();
            assertThat(fetchedScopes).containsExactly("MembershipCredential");
            assertThat(fetchedScopes).doesNotContain("Iso27001Credential");

            assertNotNull(response);
            assertNotNull(response.getPresentation());
        }

        @Test
        @DisplayName("Should return all authorized credentials when no scope requested")
        void shouldReturnAllAuthorizedWhenNoScopeRequested() {
            // Given: Access token authorizes multiple types
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of("MembershipCredential", "Iso27001Credential"))
                    .build();

            // And: Query has no scope specified
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .build();

            VerifiableCredential vc1 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc1));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should fetch all authorized types
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedScopes = scopeCaptor.getValue();
            assertThat(fetchedScopes).containsExactlyInAnyOrder("MembershipCredential", "Iso27001Credential");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should return empty when requested scopes don't match authorized")
        void shouldReturnEmptyWhenNoScopeMatch() {
            // Given: Access token authorizes only MembershipCredential
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of("MembershipCredential"))
                    .build();

            // And: Query requests only Iso27001Credential (not authorized)
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("Iso27001Credential"))
                    .build();

            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should not fetch any credentials (empty intersection)
            verify(credentialRepository, never()).findByCredentialTypeIn(anyList());

            // Response should be empty
            assertNotNull(response);
            assertNotNull(response.getPresentation());
            assertThat(response.getPresentation()).isEmpty();
        }

        @Test
        @DisplayName("Should allow all when no scopes in access token")
        void shouldAllowAllWhenNoAuthorizedScopes() {
            // Given: Access token has no scope claim
            JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

            // And: Query requests specific types
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("AnyCredential"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("AnyCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJno25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should respect query scopes (no restriction from token)
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedScopes = scopeCaptor.getValue();
            assertThat(fetchedScopes).containsExactly("AnyCredential");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should handle space-delimited scope string (OAuth 2.0 style)")
        void shouldHandleSpaceDelimitedScopes() {
            // Given: Access token has space-delimited scope string
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", "MembershipCredential Iso27001Credential")
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("MembershipCredential"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should correctly parse space-delimited scopes and match
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedScopes = scopeCaptor.getValue();
            assertThat(fetchedScopes).containsExactly("MembershipCredential");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should handle complex namespaced scope format")
        void shouldHandleComplexNamespacedScope() {
            // Given: Access token with complex namespaced scope
            String complexScope = "org.eclipse.dspace.dcp.vc.type:SomeOtherCredential:read";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(complexScope, "MembershipCredential"))
                    .build();

            // And: Query requests both complex and simple scopes
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(complexScope, "Iso27001Credential"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("SomeOtherCredential")  // Stored without action suffix
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should strip :read and query by base type only
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedScopes = scopeCaptor.getValue();
            // Action suffix stripped: "org.eclipse.dspace.dcp.vc.type:SomeOtherCredential:read" → "SomeOtherCredential"
            assertThat(fetchedScopes).containsExactly("SomeOtherCredential");
            assertThat(fetchedScopes).doesNotContain("Iso27001Credential", "MembershipCredential");

            assertNotNull(response);
            assertNotNull(response.getPresentation());
        }

        @Test
        @DisplayName("Should handle multiple complex scopes with exact string matching")
        void shouldHandleMultipleComplexScopes() {
            // Given: Multiple complex scope formats
            String scope1 = "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read";
            String scope2 = "org.eclipse.dspace.dcp.vc.type:SomeOtherCredential:read";
            String scope3 = "org.eclipse.dspace.dcp.vc.type:BankAccount:write";

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(scope1, scope2))
                    .build();

            // And: Query requests scope1, scope2, and scope3
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(scope1, scope2, scope3))
                    .build();

            VerifiableCredential vc1 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")  // Stored without action suffix
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc2 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-456")
                    .credentialType("SomeOtherCredential")  // Stored without action suffix
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc1, vc2));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should strip action suffixes and query by base types only
            // scope1 "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read" → "MembershipCredential"
            // scope2 "org.eclipse.dspace.dcp.vc.type:SomeOtherCredential:read" → "SomeOtherCredential"
            // scope3 "org.eclipse.dspace.dcp.vc.type:BankAccount:write" → "BankAccount" (not in authorized)
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedScopes = scopeCaptor.getValue();
            assertThat(fetchedScopes).containsExactlyInAnyOrder("MembershipCredential", "SomeOtherCredential");
            assertThat(fetchedScopes).doesNotContain("BankAccount");

            assertNotNull(response);
            assertNotNull(response.getPresentation());
        }

        @Test
        @DisplayName("Should match credentials ignoring action suffixes (action policy evaluated later)")
        void shouldNotMatchPartialScopes() {
            // Given: Access token with specific scope including :read action
            String authorizedScope = "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(authorizedScope))
                    .build();

            // And: Query requests same credential type but with :write action
            String requestedScope = "org.eclipse.dspace.dcp.vc.type:MembershipCredential:write";
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(requestedScope))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should MATCH because action suffixes are stripped for credential selection
            // Both ":read" and ":write" normalize to "MembershipCredential"
            // Action policy enforcement happens downstream in the negotiation module
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> queriedTypes = scopeCaptor.getValue();
            assertThat(queriedTypes).containsExactly("MembershipCredential");

            assertNotNull(response);
            assertThat(response.getPresentation()).isNotEmpty();
        }

        @Test
        @DisplayName("Should fetch all credentials when both scopes and claims are null")
        void shouldFetchAllWhenBothScopesNull() {
            // Given: No scopes in access token
            JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

            // And: No scopes in query
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("AnyCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findAll()).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should fetch all credentials (no restrictions)
            verify(credentialRepository).findAll();
            verify(credentialRepository, never()).findByCredentialTypeIn(anyList());

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should handle empty scope list in access token")
        void shouldHandleEmptyScopeList() {
            // Given: Empty scope list in access token
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of())
                    .build();

            // And: Query requests specific scopes
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("MembershipCredential"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should use requested scopes (empty authorized = no restriction)
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedScopes = scopeCaptor.getValue();
            assertThat(fetchedScopes).containsExactly("MembershipCredential");

            assertNotNull(response);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DCP SCOPE PARSING TESTS (Section 5.4.1.2)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DCP Scope Parsing Tests")
    @SuppressWarnings("unchecked")
    class ScopeParsingTests {

        @Test
        @DisplayName("Should parse org.eclipse.dspace.dcp.vc.type scope with :read suffix - USER SCENARIO")
        void shouldParseDcpVcTypeScopeWithReadSuffix() {
            // Given: DCP-formatted scope with :read suffix (exact user scenario)
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(
                            "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read",
                            "org.eclipse.dspace.dcp.vc.type:SensitiveDataCredential:read"
                    ))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(
                            "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read",
                            "org.eclipse.dspace.dcp.vc.type:SensitiveDataCredential:read"
                    ))
                    .build();

            // Credentials stored with simple names (as in user's example)
            VerifiableCredential vc1 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")  // Simple name
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc2 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-456")
                    .credentialType("SensitiveDataCredential")  // Simple name
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc1, vc2));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should extract simple credential types (stripping DCP prefix and :read)
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactlyInAnyOrder("MembershipCredential", "SensitiveDataCredential");

            assertNotNull(response);
            assertThat(response.getPresentation()).hasSize(1);
        }

        @Test
        @DisplayName("Should parse org.eclipse.dspace.dcp.vc.type formatted scope (standard format)")
        void shouldParseDcpVcTypeScope() {
            // Given: Standard DCP-formatted scope (per spec - no permission suffix)
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of("org.eclipse.dspace.dcp.vc.type:MembershipCredential"))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("org.eclipse.dspace.dcp.vc.type:MembershipCredential"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should extract "MembershipCredential"
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactly("MembershipCredential");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should handle mixed simple and DCP-formatted scopes")
        void shouldHandleMixedScopeFormats() {
            // Given: Mix of simple and DCP-formatted scopes
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(
                            "MembershipCredential",  // Simple
                            "org.eclipse.dspace.dcp.vc.type:SensitiveDataCredential:read"  // DCP with action
                    ))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(
                            "MembershipCredential",
                            "org.eclipse.dspace.dcp.vc.type:SensitiveDataCredential:read"
                    ))
                    .build();

            VerifiableCredential vc1 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc2 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-456")
                    .credentialType("SensitiveDataCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc1, vc2));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJno25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should strip :read for query and handle both formats
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactlyInAnyOrder("MembershipCredential", "SensitiveDataCredential");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should parse custom alias formats (implementation-specific)")
        void shouldParseCustomAliasFormats() {
            // Given: Custom alias formats (per spec line 907: MAY be implementation-specific)
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(
                            "custom.prefix:CustomCredential",
                            "myorg.credentials:EmployeeCredential:read",
                            "company.vc:PartnerCredential:write"
                    ))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(
                            "custom.prefix:CustomCredential",
                            "myorg.credentials:EmployeeCredential:read",
                            "company.vc:PartnerCredential:write"
                    ))
                    .build();

            VerifiableCredential vc1 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("CustomCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc2 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-456")
                    .credentialType("EmployeeCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc3 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-789")
                    .credentialType("PartnerCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc1, vc2, vc3));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should extract credential types from custom aliases
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactlyInAnyOrder(
                    "CustomCredential", "EmployeeCredential", "PartnerCredential");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should handle deeply nested prefix format")
        void shouldHandleDeeplyNestedPrefix() {
            // Given: Deeply nested prefix (multiple dots and colons)
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of("com.example.org.dept.credentials.v2:SpecialCredential:admin"))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("com.example.org.dept.credentials.v2:SpecialCredential:admin"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("SpecialCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should extract "SpecialCredential" regardless of prefix depth
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactly("SpecialCredential");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should handle scope with multiple colons in discriminator")
        void shouldHandleScopeWithMultipleColons() {
            // Given: Scope where discriminator itself contains colons (edge case)
            // Format: prefix:discriminator:with:colons:action
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of("myorg.prefix:credential:type:v1:read"))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("myorg.prefix:credential:type:v1:read"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("credential:type:v1")  // Stored with colons
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should extract everything after first colon, stripping :read
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactly("credential:type:v1");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should handle mix of standard DCP and custom aliases")
        void shouldHandleMixOfStandardAndCustomAliases() {
            // Given: Mix of standard DCP aliases and custom aliases
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(
                            "org.eclipse.dspace.dcp.vc.type:MembershipCredential",  // Standard DCP
                            "custom.org:EmployeeCredential:read",  // Custom
                            "SensitiveDataCredential"  // Simple
                    ))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(
                            "org.eclipse.dspace.dcp.vc.type:MembershipCredential",
                            "custom.org:EmployeeCredential:read",
                            "SensitiveDataCredential"
                    ))
                    .build();

            VerifiableCredential vc1 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc2 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-456")
                    .credentialType("EmployeeCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc3 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-789")
                    .credentialType("SensitiveDataCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc1, vc2, vc3));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should handle all three formats correctly
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactlyInAnyOrder(
                    "MembershipCredential", "EmployeeCredential", "SensitiveDataCredential");

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should match scopes with different formats (normalized comparison)")
        void shouldMatchScopesWithDifferentFormats() {
            // Given: Access token authorizes with full DCP format
            String authorizedScope = "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(authorizedScope))
                    .build();

            // And: Query requests with simple format (just credential type)
            String requestedScope = "MembershipCredential";
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(requestedScope))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should match because both normalize to "MembershipCredential"
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactly("MembershipCredential");

            assertNotNull(response);
            assertThat(response.getPresentation()).isNotEmpty();
        }

        @Test
        @DisplayName("Should match scopes with different formats reversed (simple authorized, full requested)")
        void shouldMatchScopesWithDifferentFormatsReversed() {
            // Given: Access token authorizes with simple format
            String authorizedScope = "MembershipCredential";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(authorizedScope))
                    .build();

            // And: Query requests with full DCP format
            String requestedScope = "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read";
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(requestedScope))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJno25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should match because both normalize to "MembershipCredential"
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactly("MembershipCredential");

            assertNotNull(response);
            assertThat(response.getPresentation()).isNotEmpty();
        }

        @Test
        @DisplayName("Should match multiple scopes with mixed formats")
        void shouldMatchMultipleScopesWithMixedFormats() {
            // Given: Access token with mixed scope formats
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(
                            "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read",
                            "EmployeeCredential",
                            "org.eclipse.dspace.dcp.vc.type:BankAccount:write"
                    ))
                    .build();

            // And: Query requests with different formats
            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(
                            "MembershipCredential",  // matches first authorized (normalized)
                            "org.eclipse.dspace.dcp.vc.type:EmployeeCredential:read",  // matches second (normalized)
                            "ContractCredential"  // NOT authorized
                    ))
                    .build();

            VerifiableCredential vc1 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-123")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc2 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:test-456")
                    .credentialType("EmployeeCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vc1, vc2));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should match first two (MembershipCredential and EmployeeCredential)
            // ContractCredential should be excluded (not authorized)
            // BankAccount should be excluded (not requested)
            // Action suffixes stripped for querying
            ArgumentCaptor<List<String>> scopeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(scopeCaptor.capture());

            List<String> fetchedTypes = scopeCaptor.getValue();
            assertThat(fetchedTypes).containsExactlyInAnyOrder("MembershipCredential", "EmployeeCredential");
            assertThat(fetchedTypes).doesNotContain("ContractCredential", "BankAccount");

            assertNotNull(response);
            assertThat(response.getPresentation()).isNotEmpty();
        }

        @Test
        @DisplayName("Should query credentials by ID using org.eclipse.dspace.dcp.vc.id alias")
        void shouldQueryCredentialsByIdAlias() {
            // Given: Scope using vc.id alias (per DCP spec Section 5.4.1.2.2)
            String credentialId = "urn:uuid:8247b87d-8d72-47e1-8128-9ce47e3d829d";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of("org.eclipse.dspace.dcp.vc.id:" + credentialId))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("org.eclipse.dspace.dcp.vc.id:" + credentialId))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id(credentialId)
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByIdIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should query by ID, not by type
            ArgumentCaptor<List<String>> idCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByIdIn(idCaptor.capture());
            verify(credentialRepository, never()).findByCredentialTypeIn(anyList());

            List<String> queriedIds = idCaptor.getValue();
            assertThat(queriedIds).containsExactly(credentialId);

            assertNotNull(response);
            assertThat(response.getPresentation()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle multiple credentials queried by ID")
        void shouldHandleMultipleCredentialsByIdAlias() {
            // Given: Multiple credentials requested by ID
            String id1 = "urn:uuid:credential-001";
            String id2 = "urn:uuid:credential-002";
            String id3 = "urn:uuid:credential-003";

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(
                            "org.eclipse.dspace.dcp.vc.id:" + id1,
                            "org.eclipse.dspace.dcp.vc.id:" + id2,
                            "org.eclipse.dspace.dcp.vc.id:" + id3
                    ))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(
                            "org.eclipse.dspace.dcp.vc.id:" + id1,
                            "org.eclipse.dspace.dcp.vc.id:" + id2,
                            "org.eclipse.dspace.dcp.vc.id:" + id3
                    ))
                    .build();

            VerifiableCredential vc1 = VerifiableCredential.Builder.newInstance()
                    .id(id1)
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc2 = VerifiableCredential.Builder.newInstance()
                    .id(id2)
                    .credentialType("EmployeeCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vc3 = VerifiableCredential.Builder.newInstance()
                    .id(id3)
                    .credentialType("BankCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByIdIn(anyList())).thenReturn(List.of(vc1, vc2, vc3));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should query all three by ID
            ArgumentCaptor<List<String>> idCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByIdIn(idCaptor.capture());

            List<String> queriedIds = idCaptor.getValue();
            assertThat(queriedIds).containsExactlyInAnyOrder(id1, id2, id3);

            assertNotNull(response);
            assertThat(response.getPresentation()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle mixed vc.type and vc.id scopes in same query")
        void shouldHandleMixedTypeAndIdScopes() {
            // Given: Mix of type-based and id-based scopes
            String credentialId = "urn:uuid:specific-credential";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of(
                            "org.eclipse.dspace.dcp.vc.type:MembershipCredential",
                            "org.eclipse.dspace.dcp.vc.id:" + credentialId,
                            "org.eclipse.dspace.dcp.vc.type:EmployeeCredential"
                    ))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of(
                            "org.eclipse.dspace.dcp.vc.type:MembershipCredential",
                            "org.eclipse.dspace.dcp.vc.id:" + credentialId,
                            "org.eclipse.dspace.dcp.vc.type:EmployeeCredential"
                    ))
                    .build();

            // Credentials queried by type
            VerifiableCredential vcType1 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:type-based-1")
                    .credentialType("MembershipCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            VerifiableCredential vcType2 = VerifiableCredential.Builder.newInstance()
                    .id("urn:uuid:type-based-2")
                    .credentialType("EmployeeCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            // Credential queried by ID
            VerifiableCredential vcId = VerifiableCredential.Builder.newInstance()
                    .id(credentialId)
                    .credentialType("SpecialCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByCredentialTypeIn(anyList())).thenReturn(List.of(vcType1, vcType2));
            when(credentialRepository.findByIdIn(anyList())).thenReturn(List.of(vcId));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should query both by type and by ID
            ArgumentCaptor<List<String>> typeCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByCredentialTypeIn(typeCaptor.capture());

            ArgumentCaptor<List<String>> idCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByIdIn(idCaptor.capture());

            List<String> queriedTypes = typeCaptor.getValue();
            assertThat(queriedTypes).containsExactlyInAnyOrder("MembershipCredential", "EmployeeCredential");

            List<String> queriedIds = idCaptor.getValue();
            assertThat(queriedIds).containsExactly(credentialId);

            assertNotNull(response);
            assertThat(response.getPresentation()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle vc.id with action suffix (stripped for query)")
        void shouldHandleVcIdWithActionSuffix() {
            // Given: vc.id scope with action suffix
            // Action suffixes are stripped for querying but can be used for downstream policy evaluation
            String credentialId = "urn:uuid:credential-with-action";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("scope", List.of("org.eclipse.dspace.dcp.vc.id:" + credentialId + ":read"))
                    .build();

            PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                    .scope(List.of("org.eclipse.dspace.dcp.vc.id:" + credentialId + ":read"))
                    .build();

            VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                    .id(credentialId)  // Stored without action suffix
                    .credentialType("SomeCredential")
                    .holderDid("did:web:localhost:8080")
                    .profileId(ProfileId.VC20_BSSL_JWT)
                    .build();

            when(credentialRepository.findByIdIn(anyList())).thenReturn(List.of(vc));
            when(vpSigner.sign(any(VerifiablePresentation.class), eq("jwt")))
                    .thenReturn("eyJhbGciOiJub25lIn0.eyJ2cElkIjoidGVzdCJ9.");

            // When
            PresentationResponseMessage response = presentationService.createPresentation(query, claims);

            // Then: Should query with :read stripped (base ID only)
            ArgumentCaptor<List<String>> idCaptor = ArgumentCaptor.forClass(List.class);
            verify(credentialRepository).findByIdIn(idCaptor.capture());

            List<String> queriedIds = idCaptor.getValue();
            assertThat(queriedIds).containsExactly(credentialId);  // Action suffix stripped

            assertNotNull(response);
        }
    }
}
