package it.eng.connector.interceptor;

import it.eng.tools.service.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MVC interceptor that clears the tenant context after each request completes.
 * Works in conjunction with {@code ApiTenantContextFilter} to ensure the
 * {@link TenantContextHolder} is clean regardless of how a request terminates.
 */
@Component
public class TenantContextClearingInterceptor implements HandlerInterceptor {

    /**
     * Constructs a new {@link TenantContextClearingInterceptor}.
     */
    public TenantContextClearingInterceptor() {
    }

    /**
     * Clears the tenant context after the handler completes, even on exception.
     *
     * @param request  the current HTTP request
     * @param response the current HTTP response
     * @param handler  the handler (or handler method) that was invoked
     * @param ex       any exception thrown during handler execution, or {@code null} on success
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        TenantContextHolder.clear();
    }
}
