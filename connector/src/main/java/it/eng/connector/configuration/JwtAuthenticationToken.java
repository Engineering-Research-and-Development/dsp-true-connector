package it.eng.connector.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class JwtAuthenticationToken implements Authentication {

	private static final long serialVersionUID = -193691158965362974L;
	
	private final String token;
	private boolean isAuthenticated;
    private List<SimpleGrantedAuthority> authorities = new ArrayList<>();
    private SimpleGrantedAuthority connectorAuthority = new SimpleGrantedAuthority("ROLE_CONNECTOR");
    
	public JwtAuthenticationToken(String token) {
		this.token = token;
		authorities.add(connectorAuthority);
	}
	public JwtAuthenticationToken(String token, boolean isAuthenticated) {
		this.token = token;
		this.isAuthenticated = isAuthenticated;
		authorities.add(connectorAuthority);
	}
	
	@Override
	public String getName() {
		return null;
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
		return null;
	}

	@Override
	public String getPrincipal() {
		return token;
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
