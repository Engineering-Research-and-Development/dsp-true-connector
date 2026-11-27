package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantState;
import it.eng.dcp.common.participant.SigningKeyReference;
import it.eng.dcp.common.model.CredentialOfferMessage;
import it.eng.dcp.common.service.IssuerTrustService;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.holder.exception.CredentialOfferForbiddenException;
import it.eng.dcp.holder.repository.CredentialStatusRepository;
import it.eng.dcp.holder.repository.VerifiableCredentialRepository;
import it.eng.dcp.holder.service.CredentialIssuanceClient;
import it.eng.dcp.holder.service.HolderService;
import it.eng.dcp.holder.service.PresentationRateLimiter;
import it.eng.dcp.holder.service.PresentationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for the credential offer authorization of {@link HolderService} (DCP v1.0.1 §6.6) and for
 * the trusted-issuer guard on sparse offer resolution.
 */
@ExtendWith(MockitoExtension.class)
class HolderServiceCredentialOfferTest {

    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String OTHER_DID = "did:web:attacker.example.com";
    private static final String TOKEN = "issuer-token";

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @Mock
    private PresentationService presentationService;

    @Mock
    private PresentationRateLimiter rateLimiter;

    @Mock
    private VerifiableCredentialRepository credentialRepository;

    @Mock
    private CredentialStatusRepository credentialStatusRepository;

    @Mock
    private CredentialIssuanceClient issuanceClient;

    @Mock
    private IssuerTrustService issuerTrustService;

    @Mock
    private DcpAuditEventPublisher auditPublisher;

    private HolderService holderService;

    private final ParticipantContext participant = new ParticipantContext("default", "did:example:holder",
            new SigningKeyReference("eckey.p12", "alias"), List.of(), Map.of(), null, ParticipantState.ENABLED);

    @BeforeEach
    void setUp() {
        holderService = new HolderService(tokenService, presentationService, rateLimiter, credentialRepository,
                credentialStatusRepository, new ObjectMapper(), issuanceClient, issuerTrustService, auditPublisher);
    }

    private static JWTClaimsSet claimsFor(String did) {
        return new JWTClaimsSet.Builder().issuer(did).subject(did).build();
    }

    @Test
    @DisplayName("authorizeCredentialOffer returns the issuer DID for a valid token of a trusted issuer")
    void authorizeCredentialOffer_trustedIssuer_returnsIssuerDid() {
        when(tokenService.validateToken(TOKEN, participant)).thenReturn(claimsFor(ISSUER_DID));
        when(issuerTrustService.isTrustedIssuer(ISSUER_DID)).thenReturn(true);

        assertEquals(ISSUER_DID, holderService.authorizeCredentialOffer(TOKEN, ISSUER_DID, participant));
    }

    @Test
    @DisplayName("authorizeCredentialOffer accepts a URL-encoded offer issuer equal to the token subject")
    void authorizeCredentialOffer_encodedIssuer_matchesSubject() {
        String plain = "did:web:localhost:8084:issuer";
        when(tokenService.validateToken(TOKEN, participant)).thenReturn(claimsFor(plain));
        when(issuerTrustService.isTrustedIssuer(plain)).thenReturn(true);

        assertEquals(plain, holderService.authorizeCredentialOffer(TOKEN, "did:web:localhost%3A8084:issuer", participant));
    }

    @Test
    @DisplayName("authorizeCredentialOffer rejects a missing token with SecurityException and audits it")
    void authorizeCredentialOffer_missingToken_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> holderService.authorizeCredentialOffer(null, ISSUER_DID, participant));
        assertThrows(SecurityException.class, () -> holderService.authorizeCredentialOffer(" ", ISSUER_DID, participant));

        verify(tokenService, never()).validateToken(anyString(), eq(participant));
        verify(auditPublisher, times(2)).publishEvent(eq(DcpAuditEventType.TOKEN_VALIDATION_FAILED),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("authorizeCredentialOffer propagates SecurityException for an invalid token")
    void authorizeCredentialOffer_invalidToken_throwsSecurityException() {
        when(tokenService.validateToken(TOKEN, participant)).thenThrow(new SecurityException("Invalid signature"));

        assertThrows(SecurityException.class, () -> holderService.authorizeCredentialOffer(TOKEN, ISSUER_DID, participant));
        verifyNoInteractions(issuerTrustService);
    }

    @Test
    @DisplayName("authorizeCredentialOffer rejects a token whose sub differs from the offer issuer")
    void authorizeCredentialOffer_subjectMismatch_throwsForbidden() {
        when(tokenService.validateToken(TOKEN, participant)).thenReturn(claimsFor(OTHER_DID));

        assertThrows(CredentialOfferForbiddenException.class,
                () -> holderService.authorizeCredentialOffer(TOKEN, ISSUER_DID, participant));
        verify(issuerTrustService, never()).isTrustedIssuer(anyString());
        verify(auditPublisher).publishEvent(eq(DcpAuditEventType.TOKEN_VALIDATION_FAILED),
                any(), any(), any(), eq(OTHER_DID), any(), any(), any());
    }

    @Test
    @DisplayName("authorizeCredentialOffer rejects an issuer that is not trusted")
    void authorizeCredentialOffer_untrustedIssuer_throwsForbidden() {
        when(tokenService.validateToken(TOKEN, participant)).thenReturn(claimsFor(OTHER_DID));
        when(issuerTrustService.isTrustedIssuer(OTHER_DID)).thenReturn(false);

        assertThrows(CredentialOfferForbiddenException.class,
                () -> holderService.authorizeCredentialOffer(TOKEN, OTHER_DID, participant));
        verify(auditPublisher).publishEvent(eq(DcpAuditEventType.TOKEN_VALIDATION_FAILED),
                any(), any(), any(), eq(OTHER_DID), any(), any(), any());
    }

    @Test
    @DisplayName("authorizeCredentialOffer rejects an offer without issuer after authenticating the token")
    void authorizeCredentialOffer_missingOfferIssuer_throwsIllegalArgument() {
        when(tokenService.validateToken(TOKEN, participant)).thenReturn(claimsFor(ISSUER_DID));

        assertThrows(IllegalArgumentException.class, () -> holderService.authorizeCredentialOffer(TOKEN, null, participant));
    }

    @Test
    @DisplayName("processCredentialOffer does not fetch metadata for a sparse offer of an untrusted issuer")
    void processCredentialOffer_sparseUntrustedIssuer_doesNotFetchMetadata() {
        CredentialOfferMessage.CredentialObject sparse = mock(CredentialOfferMessage.CredentialObject.class);
        when(sparse.getId()).thenReturn("cred-123");
        when(sparse.getCredentialType()).thenReturn(null);
        CredentialOfferMessage offer = mock(CredentialOfferMessage.class);
        when(offer.getIssuer()).thenReturn(OTHER_DID);
        when(offer.getCredentialObjects()).thenReturn(List.of(sparse));
        when(issuerTrustService.isTrustedIssuer(OTHER_DID)).thenReturn(false);

        assertThrows(CredentialOfferForbiddenException.class, () -> holderService.processCredentialOffer(offer, participant));
        verifyNoInteractions(issuanceClient);
    }
}
