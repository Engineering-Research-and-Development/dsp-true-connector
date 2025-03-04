package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationPropertyNotFoundAPIExceptionTest {

    @Test
    @DisplayName("Create ApplicationPropertyNotFoundAPIException with default constructor")
    void createApplicationPropertyNotFoundAPIException_defaultConstructor_success() {
        ApplicationPropertyNotFoundAPIException exception = new ApplicationPropertyNotFoundAPIException();
        assertNotNull(exception);
    }

    @Test
    @DisplayName("Create ApplicationPropertyNotFoundAPIException with message constructor")
    void createApplicationPropertyNotFoundAPIException_messageConstructor_success() {
        String errorMessage = "Application property not found";
        ApplicationPropertyNotFoundAPIException exception = new ApplicationPropertyNotFoundAPIException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
    }
}
