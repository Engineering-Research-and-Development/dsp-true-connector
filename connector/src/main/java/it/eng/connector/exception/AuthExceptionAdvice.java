package it.eng.connector.exception;

import it.eng.connector.rest.api.AuthController;
import java.time.ZonedDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Dedicated exception advice for {@link AuthController}.
 *
 * <p>Scoped with {@code assignableTypes = AuthController.class} rather than a {@code basePackages}
 * value, so it is strictly narrower than the broader {@code ExceptionAPIAdvice} (which is scoped to
 * {@code it.eng.connector.rest.api} / {@code it.eng.tools.rest.api} at
 * {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}). Spring resolves the most specific
 * applicable advice per exception type, so this advice only declares handlers for exception types
 * {@code ExceptionAPIAdvice} does not already handle, avoiding any ambiguous overlap.
 *
 * <p>Authentication failures ({@link BadCredentialsException}, {@link DisabledException},
 * {@link LockedException}) all map to an identical, generic {@code 401} body so that clients cannot
 * distinguish a wrong password from a disabled or locked account. Bean-validation failures map to
 * {@code 400}. Any other unexpected exception maps to a masked {@code 500} that never leaks
 * internal exception detail.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionAdvice extends ResponseEntityExceptionHandler {

    private static final String GENERIC_AUTH_FAILURE_MESSAGE = "Invalid credentials";
    private static final String GENERIC_SERVER_ERROR_MESSAGE = "An unexpected error occurred";

    /**
     * Maps {@link BadCredentialsException}, {@link DisabledException}, and {@link LockedException}
     * to an identical {@code 401} body, so the response never reveals whether credentials were
     * wrong or the account is disabled/locked.
     *
     * @param ex      the authentication exception
     * @param request the web request
     * @return {@code 401 Unauthorized} with a generic error body
     */
    @ExceptionHandler(value = {BadCredentialsException.class, DisabledException.class, LockedException.class})
    protected ResponseEntity<Object> handleAuthenticationFailure(RuntimeException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_FAILURE_MESSAGE);
    }

    /**
     * Maps any other unexpected exception raised from {@link AuthController} to a masked
     * {@code 500} response that does not leak internal exception detail such as stack trace text
     * or class names.
     *
     * @param ex      the unexpected exception
     * @param request the web request
     * @return {@code 500 Internal Server Error} with a generic, masked error body
     */
    @ExceptionHandler(value = {Exception.class})
    protected ResponseEntity<Object> handleUnexpectedException(Exception ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SERVER_ERROR_MESSAGE);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request body");
    }

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message) {
        AuthErrorResponse body =
                new AuthErrorResponse(ZonedDateTime.now(), status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(body);
    }
}
