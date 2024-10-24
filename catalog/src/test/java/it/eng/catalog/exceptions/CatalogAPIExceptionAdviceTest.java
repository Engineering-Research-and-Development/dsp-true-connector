package it.eng.catalog.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class CatalogAPIExceptionAdviceTest {

	private final String TEST_ERROR_MESSAGE = "Test error message";
	
	private MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
	private final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
	private WebRequest request = new ServletWebRequest(this.servletRequest, this.servletResponse);

	private CatalogAPIExceptionAdvice advice = new CatalogAPIExceptionAdvice();
	
	@Test
	public void handleCatalogNotFoundException() {
		ResourceNotFoundAPIException ex = new ResourceNotFoundAPIException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleResourceNotFoundAPIException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void handleCatalogErrorAPIException() {
		CatalogErrorAPIException ex = new CatalogErrorAPIException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleCatalogErrorAPIException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}
	
	@Test
	public void handleDatasetNotFoundException() {
		InternalServerErrorAPIException ex = new InternalServerErrorAPIException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleInternalServerErrorAPIException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
}
