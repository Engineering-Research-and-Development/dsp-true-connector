package it.eng.connector.configuration;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import it.eng.tools.auth.keycloak.issuer.IssuerTokenValidators;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the {@link AuditEventType#SECURITY_REALM_ROLE_VIOLATION} audit event for a token that carries a
 * privileged realm role its issuer may not grant.
 *
 * <p>The event holds the realm, the issuer type, the token's {@code sub} and the request path, never the token or
 * its other claims.
 *
 * <p>The trusted-issuer registry raises the violation (error code {@link IssuerTokenValidators#REALM_ROLE_VIOLATION})
 * only from the per-issuer validators, which run after the issuer's decoder has verified the signature,
 * {@code exp}, {@code nbf} and the exact {@code iss}. Reading {@code iss} and {@code sub} from the same token
 * string afterwards therefore reads verified values, although the decoder does not hand the verified
 * {@code Jwt} back with the exception. Callers must only call {@link #audit} for such a failure.
 */
@Slf4j
final class RealmRoleViolationAuditor {

    static final String REALM_DETAIL = "realm";
    static final String ISSUER_TYPE_DETAIL = "issuerType";
    static final String SUBJECT_DETAIL = "subject";
    static final String PATH_DETAIL = "path";

    private static final String DESCRIPTION = "Token rejected: it carries a privileged role its realm may not grant";
    private static final String UNKNOWN = "unknown";
    private static final int MAX_DETAIL_LENGTH = 200;

    private final KeycloakIssuerClassifier issuerClassifier;
    private final AuditEventPublisher auditEventPublisher;

    /**
     * Creates the auditor.
     *
     * @param issuerClassifier tells the issuer type and realm of a verified issuer
     * @param auditEventPublisher publishes the audit event
     * @throws NullPointerException when an argument is {@code null}
     */
    RealmRoleViolationAuditor(KeycloakIssuerClassifier issuerClassifier, AuditEventPublisher auditEventPublisher) {
        this.issuerClassifier = Objects.requireNonNull(issuerClassifier, "issuerClassifier");
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher, "auditEventPublisher");
    }

    /**
     * Tells whether a decoding failure is a realm role violation.
     *
     * @param exception the exception the decoder threw
     * @return {@code true} when it is a {@link JwtValidationException} with an error of code
     *         {@link IssuerTokenValidators#REALM_ROLE_VIOLATION}
     */
    static boolean isRealmRoleViolation(JwtException exception) {
        return exception instanceof JwtValidationException validationException
                && validationException.getErrors().stream()
                .anyMatch(error -> IssuerTokenValidators.REALM_ROLE_VIOLATION.equals(error.getErrorCode()));
    }

    /**
     * Publishes the audit event for a token that failed with a realm role violation. A failure to read the token
     * or to publish the event is logged and never propagated, so the request still continues unauthenticated.
     *
     * @param token the raw token whose decoding failed with a realm role violation
     * @param request the current HTTP request
     */
    void audit(String token, HttpServletRequest request) {
        try {
            JWTClaimsSet claims = JWTParser.parse(token).getJWTClaimsSet();
            String issuer = claims.getIssuer();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put(REALM_DETAIL, issuerClassifier.realmOf(issuer).orElse(UNKNOWN));
            details.put(ISSUER_TYPE_DETAIL, issuerClassifier.typeOf(issuer).map(Enum::name).orElse(UNKNOWN));
            details.put(SUBJECT_DETAIL, bounded(claims.getSubject()));
            details.put(PATH_DETAIL, bounded(request.getRequestURI()));
            auditEventPublisher.publishEvent(AuditEventType.SECURITY_REALM_ROLE_VIOLATION, DESCRIPTION, details);
        } catch (ParseException | RuntimeException e) {
            log.warn("Could not write the realm role violation audit event: {}", e.getClass().getSimpleName());
        }
    }

    private static String bounded(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String truncated = value.length() > MAX_DETAIL_LENGTH ? value.substring(0, MAX_DETAIL_LENGTH) : value;
        return truncated.replaceAll("\\p{Cntrl}", "_");
    }
}
