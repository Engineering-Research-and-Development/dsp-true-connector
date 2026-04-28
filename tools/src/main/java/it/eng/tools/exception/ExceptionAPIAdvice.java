package it.eng.tools.exception;

import it.eng.tools.response.GenericApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(basePackages = {"it.eng.connector.rest.api", "it.eng.tools.rest.api"})
public class ExceptionAPIAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {BadRequestException.class})
    protected ResponseEntity<Object> handleBadRequestExceptionAPIException(BadRequestException ex, WebRequest request) {
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

    @ExceptionHandler(value = {TenantNotFoundException.class})
    protected ResponseEntity<Object> handleTenantNotFoundException(TenantNotFoundException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(value = {TenantDisabledException.class})
    protected ResponseEntity<Object> handleTenantDisabledException(TenantDisabledException ex, WebRequest request) {
        return handleExceptionInternal(ex, GenericApiResponse.error(ex.getLocalizedMessage()), new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    /**
     * Wraps malformed or invalid request body errors in a {@link GenericApiResponse} so that
     * all API error responses share a consistent format. This handles, for example,
     * {@link jakarta.validation.ValidationException} thrown by model builders during
     * Jackson deserialization.
     *
     * @param ex      the exception
     * @param headers the HTTP headers
     * @param status  the HTTP status
     * @param request the web request
     * @return 400 Bad Request with {@code success: false}
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getMostSpecificCause().getLocalizedMessage();
        return handleExceptionInternal(ex, GenericApiResponse.error(message), headers, HttpStatus.BAD_REQUEST, request);
    }

}
