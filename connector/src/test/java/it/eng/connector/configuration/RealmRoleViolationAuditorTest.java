package it.eng.connector.configuration;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import it.eng.tools.auth.keycloak.issuer.IssuerTokenValidators;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealmRoleViolationAuditorTest {

    private static final String TENANT_ISSUER = "http://kc/realms/tenant-EngineeringA";

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Captor
    private ArgumentCaptor<Map<String, Object>> details;

    @Test
    @DisplayName("Only a validation failure with the realm role violation code is a violation")
    void isRealmRoleViolation() {
        OAuth2Error violation = new OAuth2Error(IssuerTokenValidators.REALM_ROLE_VIOLATION);
        OAuth2Error invalid = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN);

        assertTrue(RealmRoleViolationAuditor.isRealmRoleViolation(validationFailure(violation)));
        assertTrue(RealmRoleViolationAuditor.isRealmRoleViolation(validationFailure(invalid, violation)));
        assertFalse(RealmRoleViolationAuditor.isRealmRoleViolation(validationFailure(invalid)));
        assertFalse(RealmRoleViolationAuditor.isRealmRoleViolation(new BadJwtException("Untrusted issuer")));
        assertFalse(RealmRoleViolationAuditor.isRealmRoleViolation(new JwtException(
                IssuerTokenValidators.REALM_ROLE_VIOLATION)));
    }

    @Test
    @DisplayName("The event holds realm, issuer type, subject and path, and no other claim")
    void auditPublishesEventWithoutClaims() {
        RealmRoleViolationAuditor auditor = auditor();
        String token = token(new JWTClaimsSet.Builder()
                .issuer(TENANT_ISSUER)
                .subject("user-1")
                .claim("email", "admin@mail.com")
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "SUPER_ADMIN")))
                .build());

        auditor.audit(token, new MockHttpServletRequest("GET", "/api/v1/tenants"));

        verify(auditEventPublisher).publishEvent(eq(AuditEventType.SECURITY_REALM_ROLE_VIOLATION), anyString(),
                details.capture());
        assertEquals(Map.of(
                RealmRoleViolationAuditor.REALM_DETAIL, "tenant-EngineeringA",
                RealmRoleViolationAuditor.ISSUER_TYPE_DETAIL, "TENANT",
                RealmRoleViolationAuditor.SUBJECT_DETAIL, "user-1",
                RealmRoleViolationAuditor.PATH_DETAIL, "/api/v1/tenants"), details.getValue());
    }

    @Test
    @DisplayName("A platform violation names the platform realm and type")
    void auditNamesPlatformIssuer() {
        String token = token(new JWTClaimsSet.Builder().issuer("http://kc/realms/platform").subject("svc").build());

        auditor().audit(token, new MockHttpServletRequest("POST", "/catalog/request"));

        verify(auditEventPublisher).publishEvent(eq(AuditEventType.SECURITY_REALM_ROLE_VIOLATION), anyString(),
                details.capture());
        assertEquals("platform", details.getValue().get(RealmRoleViolationAuditor.REALM_DETAIL));
        assertEquals("PLATFORM", details.getValue().get(RealmRoleViolationAuditor.ISSUER_TYPE_DETAIL));
    }

    @Test
    @DisplayName("An unreadable token or a failing publisher never propagates")
    void auditNeverThrows() {
        RealmRoleViolationAuditor auditor = auditor();

        auditor.audit("not-a-jwt", new MockHttpServletRequest());
        verifyNoInteractions(auditEventPublisher);

        doThrow(new IllegalStateException("down")).when(auditEventPublisher)
                .publishEvent(any(AuditEventType.class), anyString(), anyMap());
        auditor.audit(token(new JWTClaimsSet.Builder().issuer(TENANT_ISSUER).build()), new MockHttpServletRequest());
    }

    private RealmRoleViolationAuditor auditor() {
        return new RealmRoleViolationAuditor(KeycloakRealmRoleConverterTest.classifier(), auditEventPublisher);
    }

    private static JwtValidationException validationFailure(OAuth2Error... errors) {
        return new JwtValidationException("rejected", List.of(errors));
    }

    private static String token(JWTClaimsSet claims) {
        return new PlainJWT(claims).serialize();
    }
}
