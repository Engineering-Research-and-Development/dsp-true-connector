package it.eng.connector.model;

/**
 * Application roles assigned to connector users.
 *
 * <p>Use {@link #authorityName()} when constructing Spring Security authority strings
 * (e.g. for {@code SimpleGrantedAuthority}) — it prepends the {@code ROLE_} prefix required
 * by Spring Security's {@code hasRole()} / {@code hasAnyRole()} methods.
 */
public enum Role {
    USER, ADMIN, CONNECTOR, SUPER_ADMIN;

    /**
     * Returns the Spring Security authority string for this role (e.g. {@code "ROLE_ADMIN"}).
     *
     * @return the role name prefixed with {@code ROLE_}
     */
    public String authorityName() {
        return "ROLE_" + name();
    }
}
