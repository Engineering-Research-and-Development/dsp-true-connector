package it.eng.tools.auth.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Claims of an internal-call token that {@link InternalCallTokenIssuer#verify(String)} accepted.
 *
 * <p>Holds no token value, so it is safe to log.
 *
 * @param roles    the granted authorities, for example {@value InternalCallTokenIssuer#ROLE_INTERNAL}
 * @param tenantId the tenant the call runs for, empty when the call has no tenant context
 */
public record InternalCallClaims(List<String> roles, Optional<String> tenantId) {

    /**
     * Validates and copies the components.
     *
     * @param roles    the granted authorities
     * @param tenantId the tenant the call runs for
     */
    public InternalCallClaims {
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        Objects.requireNonNull(tenantId, "tenantId");
    }

    /**
     * Tells whether the token grants {@value InternalCallTokenIssuer#ROLE_INTERNAL}.
     *
     * @return {@code true} when {@value InternalCallTokenIssuer#ROLE_INTERNAL} is among the roles
     */
    public boolean hasInternalRole() {
        return roles.contains(InternalCallTokenIssuer.ROLE_INTERNAL);
    }
}
