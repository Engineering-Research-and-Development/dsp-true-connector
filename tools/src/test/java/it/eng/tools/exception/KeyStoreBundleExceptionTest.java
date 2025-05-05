package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeyStoreBundleExceptionTest {

    @Test
    @DisplayName("Create KeyStoreBundleException with default constructor")
    void createKeyStoreBundleException_defaultConstructor_success() {
        KeyStoreBundleException exception = new KeyStoreBundleException();
        assertNotNull(exception);
    }

    @Test
    @DisplayName("Create KeyStoreBundleException with message constructor")
    void createKeyStoreBundleException_messageConstructor_success() {
        String errorMessage = "KeyStore bundle error";
        KeyStoreBundleException exception = new KeyStoreBundleException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
    }
}
