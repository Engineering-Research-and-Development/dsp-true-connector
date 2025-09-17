package it.eng.connector.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;
import it.eng.connector.util.JwtTokenExtractor;

import java.io.IOException;

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

        // Only process non-API endpoints (protocol endpoints)
        // API endpoints are handled by ApiJwtAuthenticationFilter
        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = JwtTokenExtractor.extractTokenFromHeader(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Authentication authRequest = new JwtAuthenticationToken(token);
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