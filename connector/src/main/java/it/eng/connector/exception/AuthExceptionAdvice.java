package it.eng.connector.exception;

import it.eng.connector.rest.api.AuthController;
import java.time.ZonedDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Dedicated exception advice for {@link AuthController}.
 *
 * <p>Scoped with {@code assignableTypes = AuthController.class} rather than a {@code basePackages}
 * value. Spring's {@code ExceptionHandlerExceptionResolver} evaluates advice beans in ascending
 * {@code @Order} value, using the first applicable advice that declares a matching handler for the
 * thrown exception type — not the most specific selector. {@code ExceptionAPIAdvice} is ordered at
 * {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} (the minimum possible order value), so
 * it is always evaluated first for controllers in its {@code basePackages}
 * ({@code it.eng.connector.rest.api} / {@code it.eng.tools.rest.api}), which includes
 * {@link AuthController}. This advice therefore only declares handlers for exception types
 * {@code ExceptionAPIAdvice} does not already declare a handler for (so those specific types are
 * not intercepted upstream), but it cannot out-rank {@code ExceptionAPIAdvice} for exception types
 * that class already handles (for example {@code HttpMessageNotReadableException} on malformed
 * request bodies), since no advice can be ordered ahead of
 * {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}. That residual overlap is a known,
 * pre-existing limitation of the shared {@code ExceptionAPIAdvice} ordering and is out of scope for
 * this controller.
 *
 * <p>Authentication failures ({@link BadCredentialsException} and any {@link AccountStatusException}
 * — covering {@code DisabledException}, {@code LockedException}, {@code AccountExpiredException},
 * and {@code CredentialsExpiredException}) all map to an identical, generic {@code 401} body so that
 * clients cannot distinguish a wrong password from a disabled, locked, or expired account.
 * Bean-validation failures map to {@code 400}. Any other unexpected exception maps to a masked
 * {@code 500} that never leaks internal exception detail.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionAdvice extends ResponseEntityExceptionHandler {

    private static final String GENERIC_AUTH_FAILURE_MESSAGE = "Invalid credentials";
    private static final String GENERIC_SERVER_ERROR_MESSAGE = "An unexpected error occurred";

    /**
     * Maps {@link BadCredentialsException} and any {@link AccountStatusException} (disabled,
     * locked, expired account, or expired credentials) to an identical {@code 401} body, so the
     * response never reveals which specific authentication precondition failed.
     *
     * @param ex      the authentication exception
     * @param request the web request
     * @return {@code 401 Unauthorized} with a generic error body
     */
    @ExceptionHandler(value = {BadCredentialsException.class, AccountStatusException.class})
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
