package it.eng.tools.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for the shared HMAC-SHA256 JWT signing/parsing utility used by
 * {@code INTERNAL} authentication mode.
 *
 * <p>Only loaded when {@code application.auth.provider=INTERNAL} is active (see
 * {@link InternalAuthenticationModeCondition}), since Keycloak-backed and disabled authentication
 * modes never issue or validate these locally-signed tokens and therefore should not be forced to
 * configure a signing secret.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "application.security.jwt")
@Conditional(InternalAuthenticationModeCondition.class)
public class JwtProperties {

    /**
     * Shared HMAC-SHA256 signing secret, read as raw UTF-8 bytes. Must decode to at least 256 bits
     * (32 bytes). No default is provided; each environment must configure its own secret.
     */
    private String secret;

    /**
     * Access token time-to-live, in milliseconds. Defaults to 900000 (15 minutes).
     */
    private long accessExpirationMs = 900_000L;

    /**
     * Refresh token time-to-live, in milliseconds. Defaults to 604800000 (7 days).
     */
    private long refreshExpirationMs = 604_800_000L;
}
