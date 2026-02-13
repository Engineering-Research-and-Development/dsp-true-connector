package it.eng.connector.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import it.eng.tools.auth.AuthProvider;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "false", matchIfMissing = true)
public class JwtAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

	private final AuthProvider authProvider;

	public JwtAuthenticationProvider(AuthProvider authProvider) {
		this.authProvider = authProvider;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		log.debug("JWT Authenticated token");
		JwtAuthenticationToken bearer = (JwtAuthenticationToken) authentication;

		if (!authProvider.validateToken(bearer.getPrincipal())) {
			throw new BadCredentialsException("Jwt did not verified!");
		}
		// TODO consider to decode token and get connector information into authentication object
		// like reterringConnector or some other connector unique identifier, depending on the token structure
		return new JwtAuthenticationToken(bearer.getPrincipal(), true);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return JwtAuthenticationToken.class.isAssignableFrom(authentication);
	}

}
