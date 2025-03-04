package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("Create ResourceNotFoundException with default constructor")
    void createResourceNotFoundException_defaultConstructor_success() {
        ResourceNotFoundException exception = new ResourceNotFoundException();
        assertNotNull(exception);
    }

    @Test
    @DisplayName("Create ResourceNotFoundException with message constructor")
    void createResourceNotFoundException_messageConstructor_success() {
        String errorMessage = "Resource not found";
        ResourceNotFoundException exception = new ResourceNotFoundException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
    }
}
