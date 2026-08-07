package it.eng.tools.auth.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.auth.jwt.TokenPair;

/**
 * Unit tests for {@link InternalServiceTokenIssuer}.
 */
@ExtendWith(MockitoExtension.class)
class InternalServiceTokenIssuerTest {

    private static final String INTERNAL_SECRET = "super-secret-value-that-must-never-leak";

    @Mock
    private JwtService jwtService;

    @Test
    @DisplayName("Should fail startup when application.security.jwt.secret is blank")
    void blankSecretFailsStartup() {
        InternalServiceTokenIssuer issuer = new InternalServiceTokenIssuer(jwtService, "");

        IllegalStateException exception = assertThrows(IllegalStateException.class, issuer::init);
        assertNotEquals("", exception.getMessage());
    }

    @Test
    @DisplayName("Should fail startup when application.security.jwt.secret is null")
    void nullSecretFailsStartup() {
        InternalServiceTokenIssuer issuer = new InternalServiceTokenIssuer(jwtService, null);

        assertThrows(IllegalStateException.class, issuer::init);
    }

    @Test
    @DisplayName("Should succeed startup when application.security.jwt.secret is configured")
    void configuredSecretPassesStartup() {
        InternalServiceTokenIssuer issuer = new InternalServiceTokenIssuer(jwtService, INTERNAL_SECRET);

        issuer.init();
        // No exception expected.
    }

    @Test
    @DisplayName("Should mint a token for the fixed internal-service subject/email, never the secret")
    void issuesTokenWithoutLeakingSecret() {
        InternalServiceTokenIssuer issuer = new InternalServiceTokenIssuer(jwtService, INTERNAL_SECRET);
        when(jwtService.issueTokenPair("internal-service", "internal-service", List.of("ROLE_ADMIN"), null, null))
                .thenReturn(new TokenPair("access-token", "refresh-token", 900L));

        String token = issuer.issueInternalServiceToken();

        assertEquals("access-token", token);
        verify(jwtService).issueTokenPair("internal-service", "internal-service", List.of("ROLE_ADMIN"), null, null);
    }

    @Test
    @DisplayName("Should never embed the configured secret in the minted token's claims")
    void mintedTokenNeverContainsSecret() {
        InternalServiceTokenIssuer issuer = new InternalServiceTokenIssuer(jwtService, INTERNAL_SECRET);
        // Build a real JWT the same shape the issuer requests, to assert the decoded email claim
        // is the fixed identifier and not the secret - regression test for the fixed secret-leak bug.
        String realisticToken = JWT.create()
                .withSubject("internal-service")
                .withClaim("email", "internal-service")
                .withClaim("roles", List.of("ROLE_ADMIN"))
                .sign(Algorithm.HMAC256("test-signing-key"));
        when(jwtService.issueTokenPair("internal-service", "internal-service", List.of("ROLE_ADMIN"), null, null))
                .thenReturn(new TokenPair(realisticToken, "refresh-token", 900L));

        String token = issuer.issueInternalServiceToken();

        DecodedJWT decoded = JWT.decode(token);
        assertEquals("internal-service", decoded.getClaim("email").asString());
        assertNotEquals(INTERNAL_SECRET, decoded.getClaim("email").asString());
    }
}
