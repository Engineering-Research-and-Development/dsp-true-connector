package it.eng.connector.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

	@InjectMocks
	private JwtAuthenticationFilter jwtAuthenticationFilter;
	
	@Mock
	private AuthenticationManager authenticationManager;
	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpServletResponse response;
	@Mock
	private FilterChain filterChain;
	@Mock
	private Authentication authResult;
	
	@Test
	public void jwtAuthenticationFilterSuccess() throws ServletException, IOException {
		when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer validJwt");
		when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authResult);
		
		jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
		
		verify(authenticationManager).authenticate(any(Authentication.class));
	}
	
	@Test
	public void jwtAuthenticationFilterAuthenticationException() throws ServletException, IOException {
		when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer invalidJwt");
		doThrow(BadCredentialsException.class).when(authenticationManager).authenticate(any(Authentication.class));
		
		jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
		
		verify(authenticationManager, times(1)).authenticate(any(Authentication.class));
	}
	
	@Test
	public void jwtAuthenticationFilterNotJwt() throws ServletException, IOException {
		when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic usernamePasswordEncoded");
		jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

		verify(authenticationManager, times(0)).authenticate(any(Authentication.class));
	}
}
