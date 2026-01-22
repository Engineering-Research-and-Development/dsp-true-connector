package it.eng.connector.configuration;

import it.eng.dcp.common.model.PresentationResponseMessage;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Authentication token for Verifiable Credentials / Verifiable Presentations.
 * This token carries a PresentationResponseMessage and indicates whether the presentation has been validated.
 */
public class VcVpAuthenticationToken implements Authentication {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PresentationResponseMessage presentation;
    @Getter
    private final String rawToken;  // Store the raw JWT token for signature verification
    private boolean isAuthenticated;
    private final List<SimpleGrantedAuthority> authorities = new ArrayList<>();
    private final SimpleGrantedAuthority connectorAuthority = new SimpleGrantedAuthority("ROLE_CONNECTOR");
    private String subject; // DID or identifier from validated presentation

    public VcVpAuthenticationToken(PresentationResponseMessage presentation, String rawToken) {
        this.presentation = presentation;
        this.rawToken = rawToken;
        this.authorities.add(connectorAuthority);
    }

    public VcVpAuthenticationToken(PresentationResponseMessage presentation, boolean isAuthenticated, String subject) {
        this.presentation = presentation;
        this.rawToken = null;  // Not needed for authenticated token
        this.isAuthenticated = isAuthenticated;
        this.subject = subject;
        this.authorities.add(connectorAuthority);
    }

    @Override
    public String getName() {
        return subject;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return presentation;
    }

    @Override
    public PresentationResponseMessage getPrincipal() {
        return presentation;
    }

    public String getSubject() {
        return subject;
    }

    @Override
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.isAuthenticated = isAuthenticated;
    }
}

