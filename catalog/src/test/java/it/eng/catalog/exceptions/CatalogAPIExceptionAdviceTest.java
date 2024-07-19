package it.eng.catalog.exceptions;

import static org.junit.jupiter.api.Assertions.*;

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
		CatalogNotFoundAPIException ex = new CatalogNotFoundAPIException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleCatalogNotFoundException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void handleDatasetNotFoundException() {
		DatasetNotFoundAPIException ex = new DatasetNotFoundAPIException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleDatasetNotFoundException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void testHandleDataServiceNotFoundException() {
		DataServiceNotFoundAPIException ex = new DataServiceNotFoundAPIException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleDataServiceNotFoundException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void testHandleDistributionNotFoundException() {
		DistributionNotFoundAPIException ex = new DistributionNotFoundAPIException(TEST_ERROR_MESSAGE);
		ResponseEntity<Object> response = advice.handleDistributionNotFoundException(ex, request);
		assertNotNull(response);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

}
