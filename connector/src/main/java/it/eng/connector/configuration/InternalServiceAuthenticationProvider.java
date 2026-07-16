package it.eng.connector.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication provider for internal machine-to-machine service calls.
 *
 * <p>Authenticates requests made by internal services using a shared secret rather than
 * a tenant-scoped user account. The resulting principal has {@code tenantId=null}, which
 * allows {@code ApiTenantContextFilter} to honour the {@code X-Tenant-Id} request header
 * and correctly route multi-tenant internal API calls.
 *
 * <p>Only active when {@code application.auth.provider=INTERNAL}. In KEYCLOAK or DISABLED
 * modes this provider is not registered.
 */
@Slf4j
@Component
@Conditional(InternalAuthenticationModeCondition.class)
public class InternalServiceAuthenticationProvider implements AuthenticationProvider {

    /** Username used by internal services for machine-to-machine API calls. */
    static final String INTERNAL_SERVICE_USERNAME = "internal-service";

    private final String internalSecret;

    /**
     * Constructs the provider with the configured internal service secret.
     *
     * @param internalSecret the shared secret read from {@code application.internal.secret};
     *                       defaults to an empty string when the property is absent
     */
    public InternalServiceAuthenticationProvider(
            @Value("${application.internal.secret:}") String internalSecret) {
        this.internalSecret = internalSecret;
    }

    /**
     * Authenticates an {@code internal-service} request using the configured shared secret.
     *
     * <p>Returns {@code null} for any username other than {@value #INTERNAL_SERVICE_USERNAME},
     * delegating those requests to the next provider in the chain (typically
     * {@code DaoAuthenticationProvider}).
     *
     * @param authentication the authentication request containing the username and secret
     * @return a fully authenticated token whose principal has {@code tenantId=null},
     *         or {@code null} if the username does not match this provider
     * @throws AuthenticationException if the presented secret does not match
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        if (!INTERNAL_SERVICE_USERNAME.equals(username)) {
            return null;
        }
        String presentedPassword = authentication.getCredentials() != null
                ? authentication.getCredentials().toString() : "";
        if (!internalSecret.equals(presentedPassword)) {
            log.warn("InternalServiceAuthenticationProvider: bad credentials for internal-service");
            throw new BadCredentialsException("Bad credentials");
        }
        User syntheticUser = User.builder()
                .email(INTERNAL_SERVICE_USERNAME)
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        log.debug("InternalServiceAuthenticationProvider: authenticated internal-service");
        return new UsernamePasswordAuthenticationToken(syntheticUser, null, syntheticUser.getAuthorities());
    }

    /**
     * Indicates that this provider supports {@link UsernamePasswordAuthenticationToken}.
     *
     * @param authentication the authentication class to check
     * @return {@code true} when the class is assignable from {@link UsernamePasswordAuthenticationToken}
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
