package it.eng.tools.auth.internal;

import it.eng.tools.auth.AuthProvider;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.auth.jwt.TokenPair;
import it.eng.tools.service.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Conditional(InternalAuthenticationModeCondition.class)
@Slf4j
public class InternalAuthenticationService implements AuthProvider {

    /** Username used by internal services for machine-to-machine API calls. */
    static final String INTERNAL_SERVICE_USERNAME = "internal-service";

    @Value("${application.internal.secret:}")
    private String internalSecret;

    private final JwtService jwtService;

    public InternalAuthenticationService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public String fetchToken(String role) {
        String tenantId = TenantContextHolder.getTenantId();
        TokenPair tokenPair = jwtService.issueTokenPair(INTERNAL_SERVICE_USERNAME, internalSecret, List.of(role), tenantId, null);
        return tokenPair.accessToken();
    }

    @Override
    public boolean validateToken(String token) {
        // Implement your internal token validation logic here
        return "InternalTokenValue".equals(token);
    }
}
