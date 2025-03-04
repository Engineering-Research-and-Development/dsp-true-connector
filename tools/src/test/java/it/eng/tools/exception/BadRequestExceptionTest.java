package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BadRequestExceptionTest {

    @Test
    @DisplayName("Create BadRequestException with default constructor")
    void createBadRequestException_defaultConstructor_success() {
        BadRequestException exception = new BadRequestException();
        assertNotNull(exception);
    }

    @Test
    @DisplayName("Create BadRequestException with message constructor")
    void createBadRequestException_messageConstructor_success() {
        String errorMessage = "Test error message";
        BadRequestException exception = new BadRequestException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
    }
}
