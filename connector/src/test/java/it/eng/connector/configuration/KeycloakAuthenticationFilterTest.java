package it.eng.connector.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nimbusds.jose.KeySourceException;
import it.eng.connector.model.Role;
import it.eng.tools.auth.keycloak.issuer.IssuerTokenValidators;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakAuthenticationFilterTest {

    private static final String TOKEN_VALUE = "eyJ.token-value.sig";

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private RealmRoleViolationAuditor auditor;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leavesContextEmptyWhenNoBearerToken() throws Exception {
        KeycloakAuthenticationFilter filter = new KeycloakAuthenticationFilter(jwtDecoder, jwt -> List.of(), auditor);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void setsAuthenticationWhenTokenIsValid() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", "ignored")
                .build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);

        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority(Role.ADMIN.authorityName()));
        KeycloakAuthenticationFilter filter = new KeycloakAuthenticationFilter(jwtDecoder, ignored -> authorities, auditor);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .anyMatch(auth -> auth.getAuthority().equals(Role.ADMIN.authorityName())));
        verify(jwtDecoder).decode("token");
    }

    @Test
    void skipsTheInternalZone() throws Exception {
        KeycloakAuthenticationFilter filter = new KeycloakAuthenticationFilter(jwtDecoder, jwt -> List.of(), auditor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/datasets/1/formats");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer internal-call-token");
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void auditsARealmRoleViolationAndLeavesTheRequestUnauthenticated() throws Exception {
        when(jwtDecoder.decode("token")).thenThrow(new JwtValidationException("rejected", List.of(
                new OAuth2Error(IssuerTokenValidators.REALM_ROLE_VIOLATION),
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN))));
        KeycloakAuthenticationFilter filter = new KeycloakAuthenticationFilter(jwtDecoder, jwt -> List.of(), auditor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenants");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(auditor).audit("token", request);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());
    }

    @ParameterizedTest
    @MethodSource("otherFailures")
    void doesNotAuditOtherFailures(JwtException failure) throws Exception {
        when(jwtDecoder.decode("token")).thenThrow(failure);
        KeycloakAuthenticationFilter filter = new KeycloakAuthenticationFilter(jwtDecoder, jwt -> List.of(), auditor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenants");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(auditor, never()).audit(anyString(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());
    }

    @ParameterizedTest
    @MethodSource("clientTokenFailures")
    void logsATokenCausedRejectionAtDebugOnly(JwtException failure) throws Exception {
        List<ILoggingEvent> events = filterLogEvents(failure);

        assertTrue(events.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)));
        assertTrue(events.stream().anyMatch(event -> event.getLevel() == Level.DEBUG
                && event.getFormattedMessage().startsWith("Bearer token rejected: " + failure.getClass().getSimpleName())));
    }

    @Test
    void logsAKeyRetrievalFailureAtWarnWithoutTokenOrMessage() throws Exception {
        JwtException failure = new JwtException("Couldn't retrieve remote JWK set: secret-detail",
                new KeySourceException("connect timed out secret-detail"));

        List<ILoggingEvent> events = filterLogEvents(failure);

        List<ILoggingEvent> warnings = events.stream().filter(event -> event.getLevel() == Level.WARN).toList();
        assertEquals(1, warnings.size());
        String message = warnings.get(0).getFormattedMessage();
        assertTrue(message.contains("JwtException"));
        assertTrue(message.contains("KeySourceException"));
        assertFalse(message.contains("secret-detail"));
        assertTrue(events.stream().noneMatch(event -> event.getFormattedMessage().contains(TOKEN_VALUE)));
        assertNull(warnings.get(0).getThrowableProxy());
    }

    private List<ILoggingEvent> filterLogEvents(JwtException failure) throws Exception {
        when(jwtDecoder.decode(TOKEN_VALUE)).thenThrow(failure);
        KeycloakAuthenticationFilter filter = new KeycloakAuthenticationFilter(jwtDecoder, jwt -> List.of(), auditor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenants");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_VALUE);
        Logger logger = (Logger) LoggerFactory.getLogger(KeycloakAuthenticationFilter.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        return appender.list;
    }

    private static Stream<JwtException> clientTokenFailures() {
        return Stream.of(
                new JwtValidationException("rejected", List.of(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN))),
                new BadJwtException("Malformed token"));
    }

    private static Stream<JwtException> otherFailures() {
        return Stream.of(
                new JwtValidationException("rejected", List.of(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN))),
                new BadJwtException("Untrusted issuer"),
                new JwtException("Signing keys could not be loaded"));
    }
}
