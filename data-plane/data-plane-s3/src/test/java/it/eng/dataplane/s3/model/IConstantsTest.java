package it.eng.dataplane.s3.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link IConstants} — verifies constant values are stable.
 */
class IConstantsTest {

    @Test
    @DisplayName("AUTH_TYPE constant has expected value")
    void authType_hasExpectedValue() {
        assertEquals("authType", IConstants.AUTH_TYPE);
    }

    @Test
    @DisplayName("AUTHORIZATION constant has expected value")
    void authorization_hasExpectedValue() {
        assertEquals("authorization", IConstants.AUTHORIZATION);
    }
}
