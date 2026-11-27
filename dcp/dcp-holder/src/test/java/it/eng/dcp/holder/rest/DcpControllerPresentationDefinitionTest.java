package it.eng.dcp.holder.rest;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.RequestParticipantContext;
import it.eng.dcp.holder.service.HolderService;
import it.eng.dcp.holder.service.PresentationQueryAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the handling of {@code presentationDefinition} queries in {@link DcpController}.
 */
@ExtendWith(MockitoExtension.class)
class DcpControllerPresentationDefinitionTest {

    private static final String BEARER = "Bearer token";

    @Mock
    private HolderService holderService;

    @Mock
    private RequestParticipantContext requestParticipantContext;

    @Mock
    private ParticipantContext participant;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private DcpController controller;

    @BeforeEach
    void setUp() {
        controller = new DcpController(holderService, requestParticipantContext);
        lenient().when(requestParticipantContext.require(request)).thenReturn(participant);
    }

    private void authorize() throws Exception {
        when(holderService.authorizePresentationQuery(anyString(), eq(participant)))
                .thenReturn(new PresentationQueryAuthorization("did:web:verifier", "did:web:holder",
                        new JWTClaimsSet.Builder().subject("did:web:verifier").build()));
    }

    private static Map<String, Object> definition() {
        return Map.of("id", "def-1", "input_descriptors", List.of(Map.of("id", "desc-1")));
    }

    @Test
    @DisplayName("definition-only query returns 501 and creates no presentation")
    void definitionOnly_returns501() throws Exception {
        authorize();
        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .presentationDefinition(definition())
                .build();

        ResponseEntity<?> response = controller.queryPresentations(BEARER, query, request);

        assertEquals(501, response.getStatusCode().value());
        assertEquals(DcpController.PRESENTATION_DEFINITION_NOT_SUPPORTED, response.getBody());
        verify(holderService, never()).createPresentation(any(), any(), any());
        verify(holderService, never()).checkRateLimit(anyString());
    }

    @Test
    @DisplayName("query with both scope and presentationDefinition still returns 400")
    void scopeAndDefinition_returns400() throws Exception {
        authorize();
        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("org.eclipse.dspace.dcp.vc.type:MembershipCredential:read"))
                .presentationDefinition(definition())
                .build();

        ResponseEntity<?> response = controller.queryPresentations(BEARER, query, request);

        assertEquals(400, response.getStatusCode().value());
        verify(holderService, never()).createPresentation(any(), any(), any());
    }

    @Test
    @DisplayName("query with neither scope nor presentationDefinition still returns 400")
    void neither_returns400() throws Exception {
        authorize();
        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance().build();

        ResponseEntity<?> response = controller.queryPresentations(BEARER, query, request);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    @DisplayName("unauthenticated definition-only query returns 401, not 501")
    void definitionOnly_withoutAuthorization_returns401() {
        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .presentationDefinition(definition())
                .build();

        ResponseEntity<?> response = controller.queryPresentations(null, query, request);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    @DisplayName("definition-only query with an invalid token returns 401, not 501")
    void definitionOnly_invalidToken_returns401() throws Exception {
        when(holderService.authorizePresentationQuery(anyString(), eq(participant))).thenThrow(new SecurityException("bad"));
        PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
                .presentationDefinition(definition())
                .build();

        ResponseEntity<?> response = controller.queryPresentations(BEARER, query, request);

        assertEquals(401, response.getStatusCode().value());
    }
}
