package it.eng.datatransfer.filter;

import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import it.eng.datatransfer.service.AgreementService;
import it.eng.datatransfer.service.DataTransferService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Order(1)
@Slf4j
public class EndpointAvailableFilter extends OncePerRequestFilter {

	private AgreementService agreementService;
	private DataTransferService dataTransferService;
	
	public EndpointAvailableFilter(AgreementService agreementService, DataTransferService dataTransferService) {
		super();
		this.agreementService = agreementService;
		this.dataTransferService = dataTransferService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		log.info("Performing filtering...");
//		String artifactId = Stream.of(request.getRequestURI().split("/")).reduce((first,last)->last).get();
		
		String[] urlTokens = request.getRequestURI().split("/");
		String[] tokens = new String(Base64.decodeBase64URLSafe(urlTokens[2]), Charset.forName("UTF-8")).split("\\|");
		String consumerPid = tokens[0];
		String providerPid = tokens[1];
		boolean isAvailable = dataTransferService.isDataTransferStarted(consumerPid, providerPid) 
				&& agreementService.isAgreementValid(consumerPid, providerPid);
		
		if(!isAvailable) {
			log.info("Precondition not met - transfer process not started or agreement not valid!");
			response.sendError(HttpStatus.PRECONDITION_FAILED.value(), 
					"Precondition not met - transfer process not started or agreement not valid");
            return;
		}
		filterChain.doFilter(request, response);

	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		log.debug("Should filter this {}", request.getRequestURI());
		if(request.getRequestURI().contains("/artifacts/")) {
			return false;
		}
		return true;
	}

}
