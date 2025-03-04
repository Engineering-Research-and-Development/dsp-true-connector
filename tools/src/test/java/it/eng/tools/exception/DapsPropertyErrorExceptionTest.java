package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DapsPropertyErrorExceptionTest {

    @Test
    @DisplayName("Create DapsPropertyErrorException with message constructor")
    void createDapsPropertyErrorException_messageConstructor_success() {
        String errorMessage = "Daps property error";
        DapsPropertyErrorException exception = new DapsPropertyErrorException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
        assertTrue(exception instanceof ApplicationPropertyErrorException);
    }
}
