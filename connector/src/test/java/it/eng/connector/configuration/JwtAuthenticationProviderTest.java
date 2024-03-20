package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import it.eng.tools.daps.DapsService;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationProviderTest {
	
	private final static String PRINCIPAL = "principal";

	@InjectMocks
	private JwtAuthenticationProvider jwtAuthenticationProvider;
	
	@Mock
	private DapsService dapsService;
	@Mock
	private JwtAuthenticationToken authentication;
	
	@Test
	public void authenticateSuccess() {
		when(authentication.getPrincipal()).thenReturn(PRINCIPAL);
		when(dapsService.validateToken(PRINCIPAL)).thenReturn(true);
		JwtAuthenticationToken jwtAuthentication = (JwtAuthenticationToken) jwtAuthenticationProvider.authenticate(authentication);
		
		assertTrue(jwtAuthentication.isAuthenticated());
	}
	
	@Test
	public void authenticateFailed() {
		when(authentication.getPrincipal()).thenReturn(PRINCIPAL);
		when(dapsService.validateToken(PRINCIPAL)).thenReturn(false);
		
		assertThrows(BadCredentialsException.class, () -> {
			jwtAuthenticationProvider.authenticate(authentication);
		});
	}
}
