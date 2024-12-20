package it.eng.negotiation.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import it.eng.negotiation.model.NegotiationMockObjectUtil;
import jakarta.validation.ValidationException;

public class ContractNegotiationExceptionAdviceTest {

	private final String TEST_ERROR_MESSAGE = "Test error message";
	
	private MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
	private final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
	private WebRequest request = new ServletWebRequest(this.servletRequest, this.servletResponse);
	
	private ContractNegotiationExceptionAdvice advice = new ContractNegotiationExceptionAdvice();
	
	@Test
	public void handleContractNegotiationNotFoundException() {
		ContractNegotiationNotFoundException ex = new ContractNegotiationNotFoundException(TEST_ERROR_MESSAGE, NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID);
		ResponseEntity<Object> response = advice.handleContractNegotiationNotFoundException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void handleValidationException() {
		ValidationException ex = new ValidationException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleValidationException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void handleContractNegotiationExistsException() {
		ContractNegotiationExistsException ex =  new ContractNegotiationExistsException(TEST_ERROR_MESSAGE, NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID);
		ResponseEntity<Object> response = advice.handleContractNegotiationExistsException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void handleContractNegotiationInvalidStateException() {
		ContractNegotiationInvalidStateException ex = new ContractNegotiationInvalidStateException(TEST_ERROR_MESSAGE, NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID);
		ResponseEntity<Object> response = advice.handleContractNegotiationInvalidStateException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void handleContractNegotiationInvalidEventTypeException() {
		ContractNegotiationInvalidEventTypeException ex = new ContractNegotiationInvalidEventTypeException(TEST_ERROR_MESSAGE, NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID);
		ResponseEntity<Object> response = advice.handleContractNegotiationInvalidEventTypeException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void handleOfferNotFoundException() {
		OfferNotFoundException ex = new OfferNotFoundException(TEST_ERROR_MESSAGE, NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID);
		ResponseEntity<Object> response = advice.handleOfferNotFoundException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void handleProviderPidNotBlankException() {
		ProviderPidNotBlankException ex = new ProviderPidNotBlankException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleProviderPidNotBlankException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	void handleOfferNotValidException() {
		OfferNotValidException ex = new OfferNotValidException(TEST_ERROR_MESSAGE, NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID);
		ResponseEntity<Object> response = advice.handleOfferNotValidException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}
}
