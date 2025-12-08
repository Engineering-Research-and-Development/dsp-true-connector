package it.eng.catalog.exceptions;

import it.eng.tools.response.GenericApiResponse;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(basePackages = "it.eng.catalog.rest.api")
public class CatalogAPIExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {ResourceNotFoundAPIException.class})
    protected ResponseEntity<Object> handleResourceNotFoundAPIException(ResourceNotFoundAPIException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(value = {CatalogErrorAPIException.class})
    protected ResponseEntity<Object> handleCatalogErrorAPIException(CatalogErrorAPIException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(value = {InternalServerErrorAPIException.class})
    protected ResponseEntity<Object> handleInternalServerErrorAPIException(InternalServerErrorAPIException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(value = {ValidationException.class})
    protected ResponseEntity<Object> handleValidationException(ValidationException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
}
