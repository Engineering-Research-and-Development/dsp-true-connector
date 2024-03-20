package it.eng.connector.configuration;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import it.eng.tools.daps.DapsService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtAuthenticationProvider implements AuthenticationProvider {
	
	private DapsService dapsService;
	
	public JwtAuthenticationProvider(DapsService dapsService) {
		this.dapsService = dapsService;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		log.debug("JWT Authenticated token");
		JwtAuthenticationToken bearer = (JwtAuthenticationToken) authentication;

		if(!dapsService.validateToken(bearer.getPrincipal())) {
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
