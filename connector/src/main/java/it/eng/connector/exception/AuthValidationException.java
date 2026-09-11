package it.eng.connector.exception;

/**
 * Thrown when manual bean-validation of an incoming {@code /api/v1/auth/*} request body fails.
 *
 * <p>{@link it.eng.connector.rest.api.AuthController} validates its request DTOs manually
 * (via an injected {@link jakarta.validation.Validator}) instead of relying on {@code @Valid}
 * triggering {@link org.springframework.web.bind.MethodArgumentNotValidException}. The shared
 * {@code ExceptionAPIAdvice} (in {@code tools}) is ordered at
 * {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} and is applicable to
 * {@code AuthController}'s package, so it would always be consulted first by Spring's
 * {@code ExceptionHandlerExceptionResolver} and intercept
 * {@code MethodArgumentNotValidException} before {@link AuthExceptionAdvice} ever gets a chance —
 * no advice can be ordered ahead of {@code HIGHEST_PRECEDENCE}. Using this dedicated exception
 * type (which {@code ExceptionAPIAdvice} has no handler for) guarantees that
 * {@link AuthExceptionAdvice} is the one that maps bean-validation failures to the uniform
 * {@code {timestamp, status, error, message}} auth error body.
 */
public class AuthValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with the given validation failure message.
     *
     * @param message a description of the validation failure
     */
    public AuthValidationException(String message) {
        super(message);
    }
}
