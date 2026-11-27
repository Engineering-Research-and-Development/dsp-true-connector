package it.eng.dcp.holder.rest;

import it.eng.dcp.common.model.CredentialOfferMessage;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.RequestParticipantContext;
import it.eng.dcp.holder.exception.CredentialOfferForbiddenException;
import it.eng.dcp.holder.service.HolderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the authentication of {@code POST /dcp/offers} (DCP v1.0.1 §6.6) in {@link DcpController}.
 */
@ExtendWith(MockitoExtension.class)
class DcpControllerOfferTest {

    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String TOKEN = "issuer-token";
    private static final String OFFER = """
            {
              "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
              "type": "CredentialOfferMessage",
              "issuer": "did:web:issuer.example.com",
              "credentials": [
                {
                  "id": "d5c77b0e-7f4e-4fd5-8c5f-28b5fc3f96d1",
                  "type": "CredentialObject",
                  "credentialType": "MembershipCredential"
                }
              ]
            }
            """;

    @Mock
    private HolderService holderService;

    @Mock
    private RequestParticipantContext requestParticipantContext;

    @Mock
    private ParticipantContext participant;

    @InjectMocks
    private DcpController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(requestParticipantContext.require(any())).thenReturn(participant);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /dcp/offers accepts an offer from a trusted issuer with a valid token")
    void receiveOffer_validTokenTrustedIssuer_returns200() throws Exception {
        when(holderService.authorizeCredentialOffer(TOKEN, ISSUER_DID, participant)).thenReturn(ISSUER_DID);
        when(holderService.processCredentialOffer(any(CredentialOfferMessage.class), eq(participant))).thenReturn(true);

        mockMvc.perform(post("/dcp/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted", is(true)));
    }

    @Test
    @DisplayName("POST /dcp/offers returns 401 without an Authorization header")
    void receiveOffer_missingToken_returns401() throws Exception {
        mockMvc.perform(post("/dcp/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(holderService);
    }

    @Test
    @DisplayName("POST /dcp/offers returns 401 for a non-Bearer Authorization header")
    void receiveOffer_nonBearerHeader_returns401() throws Exception {
        mockMvc.perform(post("/dcp/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(holderService);
    }

    @Test
    @DisplayName("POST /dcp/offers returns 401 for an invalid token and does not process the offer")
    void receiveOffer_invalidToken_returns401() throws Exception {
        doThrow(new SecurityException("Invalid signature"))
                .when(holderService).authorizeCredentialOffer(TOKEN, ISSUER_DID, participant);

        mockMvc.perform(post("/dcp/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isUnauthorized());

        verify(holderService, never()).processCredentialOffer(any(), any());
    }

    @Test
    @DisplayName("POST /dcp/offers returns 403 when the token sub is not the offer issuer")
    void receiveOffer_subjectMismatch_returns403() throws Exception {
        doThrow(new CredentialOfferForbiddenException("Token subject does not match the offer issuer"))
                .when(holderService).authorizeCredentialOffer(TOKEN, ISSUER_DID, participant);

        mockMvc.perform(post("/dcp/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isForbidden());

        verify(holderService, never()).processCredentialOffer(any(), any());
    }

    @Test
    @DisplayName("POST /dcp/offers returns 403 when the issuer is not trusted")
    void receiveOffer_untrustedIssuer_returns403() throws Exception {
        doThrow(new CredentialOfferForbiddenException("Offer issuer is not trusted"))
                .when(holderService).authorizeCredentialOffer(TOKEN, ISSUER_DID, participant);

        mockMvc.perform(post("/dcp/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isForbidden());

        verify(holderService, never()).processCredentialOffer(any(), any());
    }

    @Test
    @DisplayName("POST /dcp/offers passes a null issuer to authorization when the offer issuer is not a string")
    void receiveOffer_nonStringIssuer_returns400() throws Exception {
        doThrow(new IllegalArgumentException("issuer must be provided"))
                .when(holderService).authorizeCredentialOffer(eq(TOKEN), isNull(), eq(participant));

        mockMvc.perform(post("/dcp/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER.replace("\"issuer\": \"did:web:issuer.example.com\"", "\"issuer\": 42")))
                .andExpect(status().isBadRequest());

        verify(holderService, never()).processCredentialOffer(any(), any());
    }

    @Test
    @DisplayName("POST /dcp/offers returns 403 when sparse resolution finds the issuer untrusted")
    void receiveOffer_processForbidden_returns403() throws Exception {
        when(holderService.authorizeCredentialOffer(anyString(), anyString(), eq(participant))).thenReturn(ISSUER_DID);
        when(holderService.processCredentialOffer(any(CredentialOfferMessage.class), eq(participant)))
                .thenThrow(new CredentialOfferForbiddenException("Offer issuer is not trusted"));

        mockMvc.perform(post("/dcp/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isForbidden());
    }
}
