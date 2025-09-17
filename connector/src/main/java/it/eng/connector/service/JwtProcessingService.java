package it.eng.connector.service;

import io.jsonwebtoken.Claims;
import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.model.Role;
import it.eng.connector.util.JwtTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for common JWT token processing operations.
 * Centralizes JWT token validation and authentication context creation.
 */
@Service
@Slf4j
public class JwtProcessingService {

    private final JwtTokenService jwtTokenService;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public JwtProcessingService(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Validates a JWT token and returns the claims.
     * 
     * @param token the JWT token to validate
     * @return Claims from the validated token
     * @throws Exception if token validation fails
     */
    public Claims validateToken(String token) throws Exception {
        return jwtTokenService.validateAccessToken(token);
    }

    /**
     * Creates an authentication context from JWT claims.
     * 
     * @param claims the JWT claims
     * @param request the HTTP request for authentication details
     * @return UsernamePasswordAuthenticationToken for the authenticated user
     */
    public UsernamePasswordAuthenticationToken createAuthenticationFromClaims(Claims claims, HttpServletRequest request) {
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        String firstName = claims.get("firstName", String.class);
        String lastName = claims.get("lastName", String.class);
        String roleStr = claims.get("role", String.class);

        Role role = Role.valueOf(roleStr);
        
        // Create user principal with user information
        ApiUserPrincipal userPrincipal = ApiUserPrincipal.builder()
                .userId(userId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .build();

        // Create authentication token
        // Spring Security's hasRole() automatically prefixes "ROLE_" to role names
        // Our Role enum already includes the ROLE_ prefix, so we need to remove it
        String roleName = role.name().startsWith("ROLE_") ? role.name().substring(5) : role.name();
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
        );

        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authToken;
    }

    /**
     * Sets the authentication in the security context.
     * 
     * @param authToken the authentication token
     */
    public void setAuthenticationInContext(UsernamePasswordAuthenticationToken authToken) {
        SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authToken);
        this.securityContextHolderStrategy.setContext(context);
    }

    /**
     * Processes JWT token from request header and sets authentication if valid.
     * 
     * @param request the HTTP request
     * @return true if authentication was set, false otherwise
     */
    public boolean processJwtTokenFromRequest(HttpServletRequest request) {
        String token = JwtTokenExtractor.extractTokenFromHeader(request);
        if (token == null) {
            return false;
        }

        try {
            Claims claims = validateToken(token);
            
            // Always set authentication for valid tokens, regardless of existing context
            // This ensures that API requests with valid JWT tokens are properly authenticated
            UsernamePasswordAuthenticationToken authToken = createAuthenticationFromClaims(claims, request);
            setAuthenticationInContext(authToken);
            
            String email = claims.get("email", String.class);
            log.debug("Set Authentication in SecurityContext for user: {}", email);
            return true;
        } catch (Exception ex) {
            log.debug("Could not set user authentication in security context: {}", ex.getMessage());
        }
        
        return false;
    }

    /**
     * Checks if the request has a valid Bearer token.
     * 
     * @param request the HTTP request
     * @return true if request has valid Bearer token, false otherwise
     */
    public boolean hasValidBearerToken(HttpServletRequest request) {
        return JwtTokenExtractor.hasValidBearerToken(request);
    }
}
