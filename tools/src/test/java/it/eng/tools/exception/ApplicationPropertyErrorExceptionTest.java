package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationPropertyErrorExceptionTest {

    @Test
    @DisplayName("Create ApplicationPropertyErrorException with default constructor")
    void createApplicationPropertyErrorException_defaultConstructor_success() {
        ApplicationPropertyErrorException exception = new ApplicationPropertyErrorException();
        assertNotNull(exception);
    }

    @Test
    @DisplayName("Create ApplicationPropertyErrorException with message constructor")
    void createApplicationPropertyErrorException_messageConstructor_success() {
        String errorMessage = "Application property error";
        ApplicationPropertyErrorException exception = new ApplicationPropertyErrorException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
    }

    @Test
    @DisplayName("Create ApplicationPropertyErrorException with message and cause constructor")
    void createApplicationPropertyErrorException_messageAndCauseConstructor_success() {
        String errorMessage = "Application property error";
        Throwable cause = new RuntimeException("Cause");
        ApplicationPropertyErrorException exception = new ApplicationPropertyErrorException(errorMessage, cause);
        assertEquals(errorMessage, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Create ApplicationPropertyErrorException with cause constructor")
    void createApplicationPropertyErrorException_causeConstructor_success() {
        Throwable cause = new RuntimeException("Cause");
        ApplicationPropertyErrorException exception = new ApplicationPropertyErrorException(cause);
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Create ApplicationPropertyErrorException with all parameters constructor")
    void createApplicationPropertyErrorException_allParametersConstructor_success() {
        String errorMessage = "Application property error";
        Throwable cause = new RuntimeException("Cause");
        boolean enableSuppression = true;
        boolean writableStackTrace = true;
        ApplicationPropertyErrorException exception = new ApplicationPropertyErrorException(
                errorMessage, cause, enableSuppression, writableStackTrace);
        assertEquals(errorMessage, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
