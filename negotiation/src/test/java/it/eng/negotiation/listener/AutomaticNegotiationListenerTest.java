package it.eng.negotiation.listener;

import it.eng.negotiation.event.AutoNegotiationAcceptedEvent;
import it.eng.negotiation.event.AutoNegotiationAgreedEvent;
import it.eng.negotiation.event.AutoNegotiationFinalizeEvent;
import it.eng.negotiation.event.AutoNegotiationVerifyEvent;
import it.eng.negotiation.service.AutomaticNegotiationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AutomaticNegotiationListenerTest {

    private static final String CN_ID = "urn:uuid:test-cn-id";

    @Mock
    private AutomaticNegotiationService automaticNegotiationService;

    @InjectMocks
    private AutomaticNegotiationListener listener;

    @Test
    @DisplayName("AutoNegotiationAgreedEvent delegates to processAgreed")
    public void handleAutoNegotiationAgreed() {
        listener.handleAutoNegotiationAgreed(new AutoNegotiationAgreedEvent(CN_ID));

        verify(automaticNegotiationService).processAgreed(CN_ID);
    }

    @Test
    @DisplayName("AutoNegotiationFinalizeEvent delegates to processFinalize")
    public void handleAutoNegotiationFinalize() {
        listener.handleAutoNegotiationFinalize(new AutoNegotiationFinalizeEvent(CN_ID));

        verify(automaticNegotiationService).processFinalize(CN_ID);
    }

    @Test
    @DisplayName("AutoNegotiationAcceptedEvent delegates to processAccepted")
    public void handleAutoNegotiationAccepted() {
        listener.handleAutoNegotiationAccepted(new AutoNegotiationAcceptedEvent(CN_ID));

        verify(automaticNegotiationService).processAccepted(CN_ID);
    }

    @Test
    @DisplayName("AutoNegotiationVerifyEvent delegates to processVerify")
    public void handleAutoNegotiationVerify() {
        listener.handleAutoNegotiationVerify(new AutoNegotiationVerifyEvent(CN_ID));

        verify(automaticNegotiationService).processVerify(CN_ID);
    }
}

