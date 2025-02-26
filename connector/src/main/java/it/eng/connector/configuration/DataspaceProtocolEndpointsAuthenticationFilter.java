package it.eng.connector.configuration;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import it.eng.tools.service.ApplicationPropertiesService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataspaceProtocolEndpointsAuthenticationFilter extends OncePerRequestFilter {

	private static final String PROTOCOL_AUTH_ENABLED = "application.protocol.authentication.enabled";
	
	private ApplicationPropertiesService applicationPropertiesService;
	
	public DataspaceProtocolEndpointsAuthenticationFilter(ApplicationPropertiesService applicationPropertiesService) {
		super();
		this.applicationPropertiesService = applicationPropertiesService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		
		String authProeprty = applicationPropertiesService.get(PROTOCOL_AUTH_ENABLED);
		boolean authEnabled = StringUtils.isNotBlank(authProeprty) ? Boolean.valueOf(authProeprty) : Boolean.TRUE;
		log.debug("Protocol endpoint authorization enabled - {}", authEnabled);
		if(authEnabled) {
			log.debug("Protocol endpoints authorization ENABLED - continue with authorization");
		} else {
			log.info("Protocol endpoints authorization DISABLED - creating dummy authorization token");
			log.debug("Protocol endpoints authorization DISABLED for uri '{}'", request.getRequestURI());
			SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
					.getContextHolderStrategy();
			
			Authentication dummyAuth = new UsernamePasswordAuthenticationToken("DAPSDisabled", "DAPSDisabled", 
					Set.of(new SimpleGrantedAuthority("ROLE_CONNECTOR")));
			
			SecurityContext context = securityContextHolderStrategy.createEmptyContext();
			context.setAuthentication(dummyAuth);
			securityContextHolderStrategy.setContext(context);
		}

		filterChain.doFilter(request, response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		String uri = request.getRequestURI();
		return uri.contains("/api");// && StringUtils.containsAny(uri, "catalog", "negotiations", "transfers", "connector");
	}
}
