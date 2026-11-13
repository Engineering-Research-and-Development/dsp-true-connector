package it.eng.connector.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Servlet filter that resolves the tenant identifier from the authenticated principal
 * and stores it in {@link TenantContextHolder} for the duration of the API request.
 * The context is always cleared in a {@code finally} block to prevent leakage.
 *
 * <p>This filter is applied only to {@code /api/**} paths, in both {@code INTERNAL} and {@code KEYCLOAK} mode.
 *
 * <p>The tenant comes from the principal: the stored tenant of a {@link User} principal, or the
 * {@value #TENANT_ID_CLAIM} claim of a {@link JwtAuthenticationToken} (INTERNAL login tokens and Keycloak
 * tenant-realm tokens carry the same claim).
 *
 * <p>The {@value #HEADER_X_TENANT_ID} request header lets a super-admin act for one tenant:
 * <ul>
 *   <li>It is honoured only for a principal holding {@code ROLE_SUPER_ADMIN} with no tenant of its own. It must
 *       name an existing, enabled tenant, otherwise the request gets 400. Every honoured delegation publishes a
 *       {@link AuditEventType#TENANT_DELEGATION} audit event.</li>
 *   <li>Any other principal (a tenant user, or one without {@code ROLE_SUPER_ADMIN}) that sends it gets 403.</li>
 *   <li>A principal holding {@code ROLE_CONNECTOR} (a machine-to-machine token) gets no tenant and the header is
 *       ignored; the chain denies the request with 403.</li>
 *   <li>A super-admin without the header keeps a {@code null} tenant (cross-tenant view).</li>
 * </ul>
 * The filter writes the 400 and 403 responses itself, in the {@link GenericApiResponse} error shape.
 */
@Slf4j
public class ApiTenantContextFilter extends OncePerRequestFilter {

    /** Request header that allows super-admin users to act on behalf of a specific tenant. */
    public static final String HEADER_X_TENANT_ID = TenantContextHolder.HEADER_X_TENANT_ID;

    /** Token claim that carries the tenant of the principal, in both authentication modes. */
    public static final String TENANT_ID_CLAIM = JwtService.TENANT_ID_CLAIM;

    static final String UNKNOWN_TENANT_MESSAGE = HEADER_X_TENANT_ID + " does not name an enabled tenant";
    static final String HEADER_NOT_ALLOWED_MESSAGE =
            HEADER_X_TENANT_ID + " is allowed only for super-admins without a tenant";

    private static final int MAX_LOGGED_LENGTH = 64;
    private static final String UNKNOWN_PRINCIPAL = "unknown";

    private final TenantRepository tenantRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Constructs a new {@link ApiTenantContextFilter}.
     *
     * @param tenantRepository    the tenant registry used to check the {@value #HEADER_X_TENANT_ID} tenant
     * @param auditEventPublisher publisher for the tenant delegation audit event
     */
    public ApiTenantContextFilter(TenantRepository tenantRepository, AuditEventPublisher auditEventPublisher) {
        this.tenantRepository = tenantRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && holdsRole(auth, Role.CONNECTOR)) {
                // A machine-to-machine (connector) principal has no tenant and may not delegate to one: the
                // request is denied with 403 by the chain, and nothing is set from the header before that.
                log.debug("Connector principal on an API route: no tenant context is set");
            } else if (auth != null && auth.isAuthenticated() && !resolveTenant(auth, request, response)) {
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    // Sets the tenant context for an authenticated, non-connector principal. Returns false when an error
    // response has been written and the chain must stop.
    private boolean resolveTenant(Authentication auth, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String ownTenantId = ownTenantId(auth);
        String headerTenantId = request.getHeader(HEADER_X_TENANT_ID);
        if (headerTenantId == null || headerTenantId.isBlank()) {
            TenantContextHolder.setTenantId(ownTenantId);
            log.debug("Set tenant context from the principal: {}", ownTenantId);
            return true;
        }
        if (ownTenantId != null || !holdsRole(auth, Role.SUPER_ADMIN)) {
            log.warn("{} header rejected for a principal that is not a super-admin without a tenant",
                    HEADER_X_TENANT_ID);
            writeError(response, HttpStatus.FORBIDDEN, HEADER_NOT_ALLOWED_MESSAGE);
            return false;
        }
        Optional<Tenant> tenant = tenantRepository.findById(headerTenantId).filter(Tenant::isEnabled);
        if (tenant.isEmpty()) {
            log.warn("{} header names no enabled tenant: {}", HEADER_X_TENANT_ID, sanitize(headerTenantId));
            writeError(response, HttpStatus.BAD_REQUEST, UNKNOWN_TENANT_MESSAGE);
            return false;
        }
        String targetTenantId = tenant.get().getId();
        TenantContextHolder.setTenantId(targetTenantId);
        // A JWT without "sub" has no name; Map.of rejects null values.
        String principal = auth.getName() != null ? auth.getName() : UNKNOWN_PRINCIPAL;
        auditEventPublisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.TENANT_DELEGATION)
                .username(auth.getName())
                .description(AuditEventType.TENANT_DELEGATION.toString())
                .details(Map.of("principal", principal, "targetTenantId", targetTenantId))
                .tenantId(targetTenantId)
                .build());
        log.debug("Super-admin acting for tenant {} from the {} header", targetTenantId, HEADER_X_TENANT_ID);
        return true;
    }

    // The tenant of the principal itself: the stored tenant of a User, the tenant_id claim of a JWT, else none.
    private static String ownTenantId(Authentication auth) {
        if (auth.getPrincipal() instanceof User user) {
            return user.getTenantId();
        }
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString(TENANT_ID_CLAIM);
        }
        return null;
    }

    private static boolean holdsRole(Authentication auth, Role role) {
        return auth.getAuthorities().stream()
                .anyMatch(authority -> role.authorityName().equals(authority.getAuthority()));
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(GenericApiResponse.error(message)));
    }

    // Header values are client input: cut them short and replace unexpected characters before they reach a log.
    private static String sanitize(String value) {
        String truncated = value.length() > MAX_LOGGED_LENGTH ? value.substring(0, MAX_LOGGED_LENGTH) : value;
        return truncated.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
