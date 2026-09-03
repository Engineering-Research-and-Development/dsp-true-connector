package it.eng.connector.exception;

import java.time.ZonedDateTime;

/**
 * Uniform error response body used by {@link AuthExceptionAdvice} for
 * {@code /api/v1/auth/*} failures.
 *
 * @param timestamp when the error was produced
 * @param status    the numeric HTTP status code
 * @param error     the HTTP status reason phrase
 * @param message   a generic, non-leaking error message
 */
public record AuthErrorResponse(ZonedDateTime timestamp, int status, String error, String message) {
}
