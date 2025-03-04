package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationPropertyAlreadyExistsAPIExceptionTest {

    @Test
    @DisplayName("Create ApplicationPropertyAlreadyExistsAPIException with message constructor")
    void createApplicationPropertyAlreadyExistsAPIException_messageConstructor_success() {
        String errorMessage = "Application property already exists";
        ApplicationPropertyAlreadyExistsAPIException exception = new ApplicationPropertyAlreadyExistsAPIException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
    }
}
