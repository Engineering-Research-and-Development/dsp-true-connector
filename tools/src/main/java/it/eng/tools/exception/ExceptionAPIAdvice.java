package it.eng.tools.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.tools.response.GenericApiResponse;

@RestControllerAdvice(basePackages = "it.eng.connector.rest.api")
public class ExceptionAPIAdvice  extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {BadRequestException.class})
    protected ResponseEntity<Object> handleBadRequestExceptionAPIException(BadRequestException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
    
    @ExceptionHandler(value = {ResourceNotFoundException.class})
    protected ResponseEntity<Object> handleResourceNotFoundExceptionAPIException(ResourceNotFoundException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }
    
}
