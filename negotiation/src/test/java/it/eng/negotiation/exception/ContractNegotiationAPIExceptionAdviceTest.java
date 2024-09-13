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

class ContractNegotiationAPIExceptionAdviceTest {

private final String TEST_ERROR_MESSAGE = "Test error message";
	
	private MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
	private final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
	private WebRequest request = new ServletWebRequest(this.servletRequest, this.servletResponse);
	
	ContractNegotiationAPIExceptionAdvice advice = new ContractNegotiationAPIExceptionAdvice();
	
	@Test
	void testHandleContractNegotiationAPIException() {
		ContractNegotiationAPIException ex = new ContractNegotiationAPIException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleContractNegotiationAPIException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

	}

}
