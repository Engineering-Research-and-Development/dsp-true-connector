package it.eng.tools.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link S3ServerException}.
 */
class S3ServerExceptionTest {

    @Test
    @DisplayName("No-args constructor creates exception that is a RuntimeException")
    void noArgsConstructor_isRuntimeException() {
        S3ServerException ex = new S3ServerException();
        assertInstanceOf(RuntimeException.class, ex);
        assertNull(ex.getMessage());
    }

    @Test
    @DisplayName("Message constructor stores the message")
    void messageConstructor_storesMessage() {
        S3ServerException ex = new S3ServerException("S3 error");
        assertEquals("S3 error", ex.getMessage());
    }

    @Test
    @DisplayName("Message + cause constructor stores both message and cause")
    void messageCauseConstructor_storesBoth() {
        RuntimeException cause = new RuntimeException("root cause");
        S3ServerException ex = new S3ServerException("S3 error", cause);
        assertEquals("S3 error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
