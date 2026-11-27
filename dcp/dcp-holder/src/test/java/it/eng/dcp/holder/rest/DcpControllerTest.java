package it.eng.dcp.holder.rest;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.RequestParticipantContext;
import it.eng.dcp.common.participant.ParticipantNotFoundException;
import it.eng.dcp.holder.service.HolderService;
import it.eng.dcp.holder.service.PresentationQueryAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the presentation query endpoint of {@link DcpController} (DCP v1.0.1 §5.3.1, §5.4.1).
 */
@ExtendWith(MockitoExtension.class)
class DcpControllerTest {

    private static final String QUERY_PATH = "/dcp/presentations/query";
    private static final String CREDENTIALS_PATH = "/dcp/credentials";
    private static final String CREDENTIAL_MESSAGE = """
            {"issuerPid":"issuer-request","status":"ISSUED","credentials":[]}
            """;
    private static final String VERIFIER_DID = "did:web:verifier.example";
    private static final String HOLDER_DID = "did:web:holder.example";
    private static final String QUERY_BODY = """
            {"@context":["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
             "type":"PresentationQueryMessage",
             "scope":["org.eclipse.dspace.dcp.vc.type:MembershipCredential"]}
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
    @DisplayName("No Authorization header gets 401")
    void queryPresentations_missingAuthorization_401() throws Exception {
        mockMvc.perform(post(QUERY_PATH).contentType(MediaType.APPLICATION_JSON).content(QUERY_BODY))
                .andExpect(status().isUnauthorized());
        verify(holderService, never()).authorizePresentationQuery(anyString(), any());
    }

    @Test
    @DisplayName("A Self-Issued ID Token without an access token gets 401 (#407 Q2)")
    void queryPresentations_missingAccessToken_401() throws Exception {
        when(holderService.authorizePresentationQuery("id-token", participant))
                .thenThrow(new SecurityException("Self-Issued ID Token carries no access token in its 'token' claim"));

        mockMvc.perform(post(QUERY_PATH)
                        .header("Authorization", "Bearer id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_BODY))
                .andExpect(status().isUnauthorized());
        verify(holderService, never()).createPresentation(any(), any(), any());
    }

    @Test
    @DisplayName("A foreign access token gets 401")
    void queryPresentations_foreignAccessToken_401() throws Exception {
        when(holderService.authorizePresentationQuery("id-token", participant))
                .thenThrow(new SecurityException("Access token was not issued by this Credential Service's holder"));

        mockMvc.perform(post(QUERY_PATH)
                        .header("Authorization", "Bearer id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_BODY))
                .andExpect(status().isUnauthorized());
        verify(holderService, never()).createPresentation(any(), any(), any());
    }

    @Test
    @DisplayName("A valid query is rate limited per Verifier DID and answered with 200")
    void queryPresentations_valid_200() throws Exception {
        PresentationQueryAuthorization authorization = new PresentationQueryAuthorization(
                VERIFIER_DID, HOLDER_DID,
                new JWTClaimsSet.Builder().subject(HOLDER_DID).claim("scope", List.of("MembershipCredential")).build());
        when(holderService.authorizePresentationQuery("id-token", participant)).thenReturn(authorization);
        when(holderService.checkRateLimit(VERIFIER_DID)).thenReturn(true);
        when(holderService.createPresentation(any(PresentationQueryMessage.class), any(PresentationQueryAuthorization.class), eq(participant)))
                .thenReturn(PresentationResponseMessage.Builder.newInstance().presentation(List.of()).build());

        mockMvc.perform(post(QUERY_PATH)
                        .header("Authorization", "Bearer id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_BODY))
                .andExpect(status().isOk());
        verify(holderService).checkRateLimit(VERIFIER_DID);
    }

    @Test
    @DisplayName("A credential message without a Holder access token gets 401 before processing")
    void receiveCredentials_missingAccessToken_401() throws Exception {
        when(holderService.authorizeIssuer("id-token", participant))
                .thenThrow(new SecurityException("Self-Issued ID Token carries no access token in its 'token' claim"));

        mockMvc.perform(post(CREDENTIALS_PATH)
                        .header("Authorization", "Bearer id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIAL_MESSAGE))
                .andExpect(status().isUnauthorized());
        verify(holderService, never()).processIssuedCredentials(any(), anyString());
        verify(holderService, never()).processRejectedCredentials(any());
    }

    @Test
    @DisplayName("A credential message with an invalid Holder access token gets 401 before processing")
    void receiveCredentials_invalidAccessToken_401() throws Exception {
        when(holderService.authorizeIssuer("id-token", participant))
                .thenThrow(new SecurityException("Holder access token is invalid"));

        mockMvc.perform(post(CREDENTIALS_PATH)
                        .header("Authorization", "Bearer id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIAL_MESSAGE))
                .andExpect(status().isUnauthorized());
        verify(holderService, never()).processIssuedCredentials(any(), anyString());
        verify(holderService, never()).processRejectedCredentials(any());
    }

    @Test
    @DisplayName("An unknown participant gets 404 before any token validation")
    void queryPresentations_unknownParticipant_404() throws Exception {
        when(requestParticipantContext.require(any())).thenThrow(new ParticipantNotFoundException("unknown"));

        mockMvc.perform(post(QUERY_PATH)
                        .header("Authorization", "Bearer id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_BODY))
                .andExpect(status().isNotFound());
        verifyNoInteractions(holderService);
    }
}
