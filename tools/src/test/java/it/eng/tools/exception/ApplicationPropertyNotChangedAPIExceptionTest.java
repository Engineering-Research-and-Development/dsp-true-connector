package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationPropertyNotChangedAPIExceptionTest {

    @Test
    @DisplayName("Create ApplicationPropertyNotChangedAPIException with default constructor")
    void createApplicationPropertyNotChangedAPIException_defaultConstructor_success() {
        ApplicationPropertyNotChangedAPIException exception = new ApplicationPropertyNotChangedAPIException();
        assertNotNull(exception);
    }

    @Test
    @DisplayName("Create ApplicationPropertyNotChangedAPIException with message constructor")
    void createApplicationPropertyNotChangedAPIException_messageConstructor_success() {
        String errorMessage = "Application property not changed";
        ApplicationPropertyNotChangedAPIException exception = new ApplicationPropertyNotChangedAPIException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
    }
}
