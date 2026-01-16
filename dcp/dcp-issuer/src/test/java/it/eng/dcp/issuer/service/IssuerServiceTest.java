package it.eng.dcp.issuer.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.issuer.repository.CredentialRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerServiceTest {
    @Mock
    private JWTClaimsSet jwtClaimsSet;
    @Mock
    private SelfIssuedIdTokenService tokenService;
    @Mock
    private CredentialRequestRepository requestRepository;
    @Mock
    private CredentialMetadataService credentialMetadataService;

    @InjectMocks
    private IssuerService issuerService;

    // --- authorizeRequest ---
    @Test
    void authorizeRequest_success() {
        String token = "token";
        when(tokenService.validateToken(token)).thenReturn(jwtClaimsSet);
        JWTClaimsSet result = issuerService.authorizeRequest(token, null);
        assertSame(jwtClaimsSet, result);
    }

    @Test
    void authorizeRequest_nullToken_throws() {
        SecurityException ex = assertThrows(SecurityException.class, () -> issuerService.authorizeRequest(null, null));
        assertTrue(ex.getMessage().contains("Bearer token is required"));
    }

    // --- createCredentialRequest ---
    @Test
    void createCredentialRequest_success() {
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        IssuerMetadata.CredentialObject credObj = mock(IssuerMetadata.CredentialObject.class);
        when(credObj.getId()).thenReturn("cred1");
        when(metadata.getCredentialsSupported()).thenReturn(List.of(credObj));
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
            .holderPid("holder1")
            .credentials(List.of(
                CredentialRequestMessage.CredentialReference.Builder.newInstance().id("cred1").build()
            ))
            .build();
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.save(any())).thenReturn(req);
        CredentialRequest result = issuerService.createCredentialRequest(msg, jwtClaimsSet.getSubject());
        assertSame(req, result);
    }

    @Test
    void createCredentialRequest_unsupportedCredential_throws() {
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        IssuerMetadata.CredentialObject credObj = mock(IssuerMetadata.CredentialObject.class);
        when(credObj.getId()).thenReturn("cred1");
        when(metadata.getCredentialsSupported()).thenReturn(List.of(credObj));
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
            .holderPid("holder1")
            .credentials(List.of(
                CredentialRequestMessage.CredentialReference.Builder.newInstance().id("bad").build()
            ))
            .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerService.createCredentialRequest(msg, jwtClaimsSet.getSubject()));
        assertTrue(ex.getMessage().contains("Credential 'bad' is not supported by this issuer."));
    }

    @Test
    void createCredentialRequest_metadataNotAvailable_throws() {
        CredentialRequestMessage msg = mock(CredentialRequestMessage.class);
        when(credentialMetadataService.buildIssuerMetadata()).thenThrow(new IllegalStateException("no meta"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> issuerService.createCredentialRequest(msg, jwtClaimsSet.getSubject()));
        assertTrue(ex.getMessage().contains("Issuer metadata not configured"));
    }

    // --- getRequestByIssuerPid ---
    @Test
    void getRequestByIssuerPid_success() {
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid("id1")).thenReturn(Optional.of(req));
        Optional<CredentialRequest> result = issuerService.getRequestByIssuerPid("id1");
        assertTrue(result.isPresent());
        assertSame(req, result.get());
    }

    @Test
    void getRequestByIssuerPid_nullOrBlank_returnsEmpty() {
        assertTrue(issuerService.getRequestByIssuerPid(null).isEmpty());
        assertTrue(issuerService.getRequestByIssuerPid("").isEmpty());
    }

    // --- getMetadata ---
    @Test
    void getMetadata_success() {
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        assertSame(metadata, issuerService.getMetadata());
    }
}
