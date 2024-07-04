package it.eng.datatransfer.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ValidationException;

class DataTransferExceptionAdviceTest {

	private final String TEST_ERROR_MESSAGE = "Test error message";
	
	private MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
	private final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
	private WebRequest request = new ServletWebRequest(this.servletRequest, this.servletResponse);

	private DataTransferExceptionAdvice advice = new DataTransferExceptionAdvice();
	
	@Test
	void handleValidationException() {
		ValidationException ex = new ValidationException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleValidationException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}
}
