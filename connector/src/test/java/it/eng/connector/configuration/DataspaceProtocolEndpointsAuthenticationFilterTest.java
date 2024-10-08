package it.eng.connector.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import it.eng.tools.service.ApplicationPropertiesService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class DataspaceProtocolEndpointsAuthenticationFilterTest {
	
	private static final String PROTOCOL_ENDPOINT = "/catalog/12";
	private static final String API_ENDPOINT = "/api/v1/catalog/12";
	private static final String PROTOCOL_AUTH_ENABLED = "application.protocol.authentication.enabled";
	
	@Mock
	private ApplicationPropertiesService applicationPropertiesService;
	
	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpServletResponse response;
	@Mock
	private FilterChain filterChain;
	
	@Mock
	SecurityContextHolder securityContextHolder;
	@Mock
	SecurityContextHolderStrategy securityContextHolderStrategy;
	@Mock
	SecurityContext securityContext;
	
	@InjectMocks
	private DataspaceProtocolEndpointsAuthenticationFilter filter;

	@Test
	void doFilter_authEnabled_protocol_endpoint() throws ServletException, IOException {
		when(request.getRequestURI()).thenReturn(PROTOCOL_ENDPOINT);
		when(applicationPropertiesService.get(PROTOCOL_AUTH_ENABLED)).thenReturn("true");
		
		filter.doFilter(request, response, filterChain);
		
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void doFilter_authEnabled_api_endpoint() throws ServletException, IOException {
		when(request.getRequestURI()).thenReturn(API_ENDPOINT);
		
		filter.doFilter(request, response, filterChain);
	}
	
	@Test
	void doFilter_authsDisabled_protocol_endpoint() throws ServletException, IOException {
		try (MockedStatic<SecurityContextHolder> mocked = mockStatic(SecurityContextHolder.class)) {
			when(request.getRequestURI()).thenReturn(PROTOCOL_ENDPOINT);
			when(applicationPropertiesService.get(PROTOCOL_AUTH_ENABLED)).thenReturn("false");

			mocked.when(SecurityContextHolder::getContextHolderStrategy).thenReturn(securityContextHolderStrategy);
			when(securityContextHolderStrategy.createEmptyContext()).thenReturn(securityContext);
			
			filter.doFilter(request, response, filterChain);
			
			verify(securityContext).setAuthentication(any(Authentication.class)); 
			verify(securityContextHolderStrategy).setContext(any(SecurityContext.class));
		 }
	}
	
	@Test
	void doFilter_authDisabled_api_endpoint() throws ServletException, IOException {
		when(request.getRequestURI()).thenReturn(API_ENDPOINT);
		
		filter.doFilter(request, response, filterChain);
	}
}
