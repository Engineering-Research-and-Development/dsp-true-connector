package it.eng.connector.exception;

import it.eng.connector.configuration.DataspaceProtocolEndpointsExceptionHandler;
import it.eng.connector.rest.api.AuthController;
import java.time.ZonedDateTime;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
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
 * {@link AuthController}, and no advice can be ordered ahead of it. This advice therefore only
 * declares handlers for exception types {@code ExceptionAPIAdvice} does not already declare a
 * handler for, so those types are never intercepted upstream:
 *
 * <ul>
 *   <li>{@link BadCredentialsException} / {@link AccountStatusException} — {@code ExceptionAPIAdvice}
 *       has no mapping for either, so this advice always wins for authentication failures.</li>
 *   <li>{@link AuthValidationException} — {@link AuthController} validates its request DTOs
 *       manually and raises this dedicated exception type instead of relying on {@code @Valid}
 *       (which would raise {@code MethodArgumentNotValidException}, a type
 *       {@code ExceptionAPIAdvice} inherits a handler for via {@code ResponseEntityExceptionHandler}
 *       and would therefore always intercept first). Because {@code ExceptionAPIAdvice} has no
 *       mapping for {@link AuthValidationException}, this advice always wins for validation
 *       failures too.</li>
 * </ul>
 *
 * <p>A residual, out-of-scope overlap remains for {@code HttpMessageNotReadableException} (malformed
 * JSON request bodies): {@code ExceptionAPIAdvice} overrides {@code handleHttpMessageNotReadable}, so
 * malformed bodies on {@code /api/v1/auth/**} are still handled by that shared advice rather than
 * this one. Resolving that fully would require narrowing {@code ExceptionAPIAdvice}'s scope, which
 * is shared across every connector API controller and out of scope for this controller.
 *
 * <p><b>Explicit {@code @Order}</b>: this advice also competes with
 * {@link DataspaceProtocolEndpointsExceptionHandler}, a separate, deliberately <em>unscoped</em>
 * {@code @ControllerAdvice} that maps {@code AuthenticationException} to DSP-protocol-shaped error
 * bodies. It must stay unscoped because it is also invoked for filter-chain-level authentication
 * rejections (missing/invalid bearer token or Basic auth header), where Spring Security's
 * {@code ExceptionTranslationFilter} calls the resolver with a {@code null} handler — and a
 * {@code basePackageClasses}/{@code assignableTypes}-scoped advice can never match a {@code null}
 * handler (see {@code HandlerTypePredicate.test}), so scoping it would silently break authentication
 * on every other protocol/admin endpoint. Since both advices default to
 * {@link Ordered#LOWEST_PRECEDENCE} when {@code @Order} is unspecified, they previously tied, and the
 * winner depended on non-deterministic controller-advice bean registration order — in practice,
 * {@code DataspaceProtocolEndpointsExceptionHandler} sometimes won for {@link AuthController}'s
 * in-controller authentication failures (e.g. login for a non-existent user), crashing with
 * {@code IllegalStateException: getInputStream() has already been called for this request} because
 * it unconditionally re-reads the request body via {@code HttpServletRequest.getReader()} — safe
 * only when the body has not yet been consumed by {@code @RequestBody} argument resolution, which is
 * never the case once a controller method has already run. Declaring {@code @Order(0)} here — lower
 * than {@code LOWEST_PRECEDENCE} but higher (less precedent) than {@code HIGHEST_PRECEDENCE} — makes
 * this advice deterministically win that tie for {@link AuthController}, while
 * {@code DataspaceProtocolEndpointsExceptionHandler} remains the correct fallback for the
 * {@code null}-handler, filter-chain-level case, which never reaches this advice at all
 * ({@code assignableTypes} cannot match a {@code null} handler either).
 *
 * <p>Authentication failures ({@link BadCredentialsException} and any {@link AccountStatusException}
 * — covering {@code DisabledException}, {@code LockedException}, {@code AccountExpiredException},
 * and {@code CredentialsExpiredException}) all map to an identical, generic {@code 401} body so that
 * clients cannot distinguish a wrong password from a disabled, locked, or expired account.
 * {@link AuthValidationException} maps to {@code 400}. Any other unexpected exception maps to a
 * masked {@code 500} that never leaks internal exception detail.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(0)
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
     * Maps manual bean-validation failures raised by {@link AuthController} to {@code 400}.
     *
     * @param ex      the validation exception, carrying the constraint violation message(s)
     * @param request the web request
     * @return {@code 400 Bad Request} with an error body describing the violation(s)
     */
    @ExceptionHandler(value = {AuthValidationException.class})
    protected ResponseEntity<Object> handleAuthValidationFailure(AuthValidationException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
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

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message) {
        AuthErrorResponse body =
                new AuthErrorResponse(ZonedDateTime.now(), status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(body);
    }
}
