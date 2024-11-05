package it.eng.connector.filters;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JsonLdRequestProcessingFilter extends OncePerRequestFilter {
	
	@Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        log.debug("JsonLDRequest filter path {}", path);
        return !(path.startsWith("/connector/") || path.startsWith("/negotiations/") 
        		|| path.startsWith("/catalog/") || path.startsWith("/transfers"));
    }

	@Override
	protected void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain)
			throws ServletException, IOException {
		filterChain.doFilter(new JsonLdCompactRequestWrapper((HttpServletRequest) servletRequest), servletResponse);
	}

}
