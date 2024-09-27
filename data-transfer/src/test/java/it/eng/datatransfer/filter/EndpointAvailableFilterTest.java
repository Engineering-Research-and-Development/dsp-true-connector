/**
 * 
 */
package it.eng.datatransfer.filter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;

import org.apache.tomcat.util.codec.binary.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import it.eng.datatransfer.service.AgreementService;
import it.eng.datatransfer.service.DataTransferService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class EndpointAvailableFilterTest {
	
	@Mock
	private AgreementService agreementService;
	@Mock
	private DataTransferService dataTransferService;
	
	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpServletResponse response;
	@Mock
	private FilterChain filterChain;
	
	@InjectMocks
	private EndpointAvailableFilter filter;

	@Test
	@DisplayName("Check artifact download allowed")
	void doFilterInternal() throws ServletException, IOException {
		String consumerPid = createNewPid();
		String providerPid = createNewPid();
		String url = new String(Base64.encodeBase64URLSafeString( (consumerPid + "|" + providerPid).getBytes(Charset.forName("UTF-8"))));
		url = "/artifacts/" + url;
		
		when(request.getRequestURI()).thenReturn(url);
		when(dataTransferService.isDataTransferStarted(consumerPid, providerPid)).thenReturn(true);
		when(agreementService.isAgreementValid(consumerPid, providerPid)).thenReturn(true);
		
		filter.doFilterInternal(request, response, filterChain);
		
		verify(filterChain).doFilter(request, response);
	}
	
	@Test
	@DisplayName("Check artifact download prohibit - dataTransfer not valid")
	void doFilterInternal_fail_dataTransfer() throws ServletException, IOException {
		String consumerPid = createNewPid();
		String providerPid = createNewPid();
		String url = new String(Base64.encodeBase64URLSafeString( (consumerPid + "|" + providerPid).getBytes(Charset.forName("UTF-8"))));
		url = "/artifacts/" + url;
		
		when(request.getRequestURI()).thenReturn(url);
		when(dataTransferService.isDataTransferStarted(consumerPid, providerPid)).thenReturn(false);
		
		filter.doFilterInternal(request, response, filterChain);
		
		verify(response).sendError(HttpStatus.PRECONDITION_FAILED.value(), 
				"Precondition not met - transfer process not started or agreement not valid");
		verify(filterChain, times(0)).doFilter(request, response);
	}
	
	@Test
	@DisplayName("Check artifact download prohibit - agreement not valid")
	void doFilterInternal_fail_agreement() throws ServletException, IOException {
		String consumerPid = createNewPid();
		String providerPid = createNewPid();
		String url = new String(Base64.encodeBase64URLSafeString( (consumerPid + "|" + providerPid).getBytes(Charset.forName("UTF-8"))));
		url = "/artifacts/" + url;
		
		when(request.getRequestURI()).thenReturn(url);
		when(dataTransferService.isDataTransferStarted(consumerPid, providerPid)).thenReturn(true);
		when(agreementService.isAgreementValid(consumerPid, providerPid)).thenReturn(false);
		
		filter.doFilterInternal(request, response, filterChain);
		
		verify(response).sendError(HttpStatus.PRECONDITION_FAILED.value(), 
				"Precondition not met - transfer process not started or agreement not valid");
		verify(filterChain, times(0)).doFilter(request, response);
	}

	 private String createNewPid() {
	        return "urn:uuid:" + UUID.randomUUID();
	    }
}
