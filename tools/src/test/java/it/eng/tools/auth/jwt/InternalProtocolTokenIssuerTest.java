package it.eng.tools.auth.jwt;

import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InternalProtocolTokenIssuer}.
 */
@ExtendWith(MockitoExtension.class)
class InternalProtocolTokenIssuerTest {

    private static final String SECRET = "unit-test-secret-value-with-at-least-256-bits!!";

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private JwtService jwtService;
    private InternalProtocolTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        jwtProperties.setAccessExpirationMs(900_000L);
        jwtProperties.setRefreshExpirationMs(604_800_000L);
        jwtService = new JwtService(jwtProperties);
        jwtService.init();
        issuer = new InternalProtocolTokenIssuer(jwtService, auditEventPublisher);
    }

    @Test
    @DisplayName("Should mint a CONNECTOR-only token that verifies with the INTERNAL key")
    void issuesLocallyMintedConnectorToken() {
        String token = issuer.issueConnectorToken();

        assertNotNull(token);
        DecodedJWT decoded = jwtService.verifyAndDecode(token);
        assertEquals(JwtService.PROTOCOL_TOKEN_SUBJECT, decoded.getSubject());
        assertEquals(Map.of(JwtService.ROLES_CLAIM, List.of(JwtService.CONNECTOR_ROLE)),
                decoded.getClaim(JwtService.REALM_ACCESS_CLAIM).asMap());
    }

    @Test
    @DisplayName("Should audit the issuance with the subject and without an email")
    @SuppressWarnings("unchecked")
    void auditsIssuanceWithoutEmail() {
        issuer.issueConnectorToken();

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.M2M_TOKEN_ISSUED), anyString(), details.capture());
        assertEquals(Map.of("subject", JwtService.PROTOCOL_TOKEN_SUBJECT), details.getValue());
    }

    @Test
    @DisplayName("Should return null and audit the failure when signing fails")
    void returnsNullWhenSigningFails() {
        JwtService failingJwtService = mock(JwtService.class);
        when(failingJwtService.issueProtocolToken()).thenThrow(new JWTCreationException("signing failed", null));
        InternalProtocolTokenIssuer failingIssuer = new InternalProtocolTokenIssuer(failingJwtService, auditEventPublisher);

        assertNull(failingIssuer.issueConnectorToken());
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.M2M_TOKEN_ISSUE_FAILED), anyString(), any());
    }

    @Test
    @DisplayName("Should be constructible without AuthService or UserRepository")
    void dependsOnlyOnJwtServiceAndAuditPublisher() {
        Constructor<?>[] constructors = InternalProtocolTokenIssuer.class.getConstructors();

        assertEquals(1, constructors.length);
        assertEquals(List.of(JwtService.class, AuditEventPublisher.class),
                Arrays.asList(constructors[0].getParameterTypes()));
        assertFalse(Arrays.stream(InternalProtocolTokenIssuer.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .anyMatch(type -> type.equals("AuthService") || type.equals("UserRepository")));
    }
}
