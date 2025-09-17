package it.eng.tools.exception;

import it.eng.tools.response.GenericApiResponse;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "it.eng.connector.rest.api")
public class ExceptionAPIAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {BadRequestException.class})
    protected ResponseEntity<Object> handleBadRequestExceptionAPIException(BadRequestException ex, WebRequest request) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(value = {ResourceNotFoundException.class})
    protected ResponseEntity<Object> handleResourceNotFoundExceptionAPIException(ResourceNotFoundException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(value = {S3ServerException.class})
    protected ResponseEntity<Object> handleS3ServerExceptionAPIException(S3ServerException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(value = {BadCredentialsException.class})
    protected ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }


    /**
     * Handle RuntimeException from JWT token validation and other runtime errors.
     *
     * @param ex      the RuntimeException
     * @param request web request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(value = {RuntimeException.class})
    protected ResponseEntity<Object> handleRuntimeException(RuntimeException ex, WebRequest request) {
        // Check if it's a JWT-related error
        String message = ex.getMessage();
        if (message != null && (message.contains("JWT token") || message.contains("token"))) {
            return handleExceptionInternal(ex, GenericApiResponse.error("Authentication failed: " + message), new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
        }

        // For other runtime exceptions, return 500 Internal Server Error
        return handleExceptionInternal(ex, GenericApiResponse.error("An unexpected error occurred"), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Handle validation errors for @Valid annotations.
     *
     * @param ex      the MethodArgumentNotValidException
     * @param headers HTTP headers
     * @param status  HTTP status code
     * @param request web request
     * @return ResponseEntity with error details
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        String errorMessage = "Validation failed: " + errors.toString();
        return handleExceptionInternal(ex, GenericApiResponse.error(errorMessage), headers, status, request);
    }

}
