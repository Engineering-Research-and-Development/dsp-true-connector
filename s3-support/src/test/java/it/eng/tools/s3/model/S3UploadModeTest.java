package it.eng.tools.s3.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link S3UploadMode#fromString(String)}.
 */
class S3UploadModeTest {

    @Test
    @DisplayName("fromString(\"SYNC\") returns SYNC")
    void fromString_sync_returnsSync() {
        assertEquals(S3UploadMode.SYNC, S3UploadMode.fromString("SYNC"));
    }

    @Test
    @DisplayName("fromString(\"ASYNC\") returns ASYNC")
    void fromString_async_returnsAsync() {
        assertEquals(S3UploadMode.ASYNC, S3UploadMode.fromString("ASYNC"));
    }

    @Test
    @DisplayName("fromString is case-insensitive")
    void fromString_caseInsensitive() {
        assertEquals(S3UploadMode.SYNC, S3UploadMode.fromString("sync"));
        assertEquals(S3UploadMode.ASYNC, S3UploadMode.fromString("async"));
        assertEquals(S3UploadMode.SYNC, S3UploadMode.fromString("Sync"));
        assertEquals(S3UploadMode.ASYNC, S3UploadMode.fromString("Async"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("fromString defaults to SYNC for null/blank values")
    void fromString_nullOrBlank_defaultsToSync(String value) {
        assertEquals(S3UploadMode.SYNC, S3UploadMode.fromString(value));
    }

    @Test
    @DisplayName("fromString defaults to SYNC for invalid/unknown values")
    void fromString_invalid_defaultsToSync() {
        assertEquals(S3UploadMode.SYNC, S3UploadMode.fromString("UNKNOWN"));
        assertEquals(S3UploadMode.SYNC, S3UploadMode.fromString("s3"));
    }
}
