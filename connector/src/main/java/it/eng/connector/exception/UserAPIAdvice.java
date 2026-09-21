package it.eng.connector.exception;

import it.eng.tools.response.GenericApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(basePackages = "it.eng.connector.rest.api")
public class UserAPIAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {UserNotFoundException.class})
    protected ResponseEntity<Object> handleUserNotFoundException(UserNotFoundException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }
}