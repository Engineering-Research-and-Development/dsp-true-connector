package it.eng.catalog.exceptions;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.catalog.rest.api.CatalogAPIController;

@RestControllerAdvice(basePackageClasses = CatalogAPIController.class)
public class CatalogAPIExceptionAdvice extends ResponseEntityExceptionHandler {
	@ExceptionHandler(value = { CatalogNotFoundAPIException.class })
	protected ResponseEntity<Object> handleCatalogNotFoundException(CatalogNotFoundAPIException ex, WebRequest request) {
		return handleExceptionInternal(ex, ex.getLocalizedMessage(), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
	}

	@ExceptionHandler(value = { DatasetNotFoundAPIException.class })
	protected ResponseEntity<Object> handleDatasetNotFoundException(DatasetNotFoundAPIException ex, WebRequest request) {
		return handleExceptionInternal(ex, ex.getLocalizedMessage(), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
	}
}
