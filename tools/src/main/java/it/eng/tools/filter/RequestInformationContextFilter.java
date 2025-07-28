package it.eng.tools.filter;

import it.eng.tools.model.RequestInfo;
import it.eng.tools.service.RequestContextHolder;
import it.eng.tools.service.RequestInfoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A filter that populates the RequestContextHolder with request information for each incoming request.
 * This allows services to access request information without it being passed explicitly from controllers.
 */
@Component
@Slf4j
public class RequestInformationContextFilter extends OncePerRequestFilter {

    private final RequestInfoService requestInfoService;

    public RequestInformationContextFilter(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Extract request information using the existing RequestInfoService
            RequestInfo requestInfo = requestInfoService.getRequestInfo(request);

            // Store it in the RequestContextHolder
            RequestContextHolder.setRequestInfo(requestInfo);

            // Continue with the filter chain
            filterChain.doFilter(request, response);
        } finally {
            // Clear the request information to prevent memory leaks
            RequestContextHolder.clearRequestInfo();
        }
    }
}