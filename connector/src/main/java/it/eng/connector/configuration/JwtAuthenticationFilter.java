package it.eng.connector.configuration;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// This class helps us to validate the generated jwt token 
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private AuthenticationManager authenticationManager;
	
	private SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
			.getContextHolderStrategy();
	
	public JwtAuthenticationFilter(AuthenticationManager authenticationManager) {
		super();
		this.authenticationManager = authenticationManager;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		
		String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}
		try {
			Authentication authRequest = new JwtAuthenticationToken(authHeader.substring(7));
			Authentication authResult = this.authenticationManager.authenticate(authRequest);
			
			SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
			context.setAuthentication(authResult);
			this.securityContextHolderStrategy.setContext(context);
			onSuccessfulAuthentication(request, response, authResult);
		} catch (AuthenticationException ex) {
			this.securityContextHolderStrategy.clearContext();
			this.logger.debug("Failed to process authentication request", ex);
			onUnsuccessfulAuthentication(request, response, ex);
		}
		// Get jwt token and validate
        filterChain.doFilter(request, response);
	}
	
	protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
			Authentication authResult) throws IOException {
		// TODO implement logic on success event
	}

	protected void onUnsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException failed) throws IOException {
		// TODO implement logic on unsuccess event
	}
}