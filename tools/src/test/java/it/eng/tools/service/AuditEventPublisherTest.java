package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private RequestInfoService requestInfoService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Audit username is the email claim when the token has one")
    void usernameIsEmailWhenPresent() {
        authenticate(Map.of("sub", "user-id", "email", "admin@mail.com"), "admin@mail.com");

        assertEquals("admin@mail.com", publishAndCapture().getUsername());
    }

    @Test
    @DisplayName("Audit username falls back to the principal name for a token without email")
    void usernameFallsBackToPrincipalNameWithoutEmail() {
        authenticate(Map.of("sub", "connector"), "connector");

        assertEquals("connector", publishAndCapture().getUsername());
    }

    private void authenticate(Map<String, Object> claims, String name) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "HS256"), claims);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
        if (!claims.containsKey("email")) {
            when(authentication.getName()).thenReturn(name);
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private AuditEvent publishAndCapture() {
        AuditEventPublisher publisher = new AuditEventPublisher(applicationEventPublisher, requestInfoService);
        publisher.publishEvent(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED, "test", Map.of());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
