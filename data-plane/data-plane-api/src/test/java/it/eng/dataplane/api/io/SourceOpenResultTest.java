package it.eng.dataplane.api.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SourceOpenResult}.
 */
class SourceOpenResultTest {

    /** Minimal InputStream that records whether {@code close()} was called. */
    private static class TrackingInputStream extends InputStream {
        boolean closed = false;

        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }

    /** InputStream whose {@code close()} always throws. */
    private static class FailingInputStream extends InputStream {
        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    @Test
    @DisplayName("success() sets all fields correctly when finite is true")
    void success_withFiniteStream_setsAllFields() {
        InputStream stream = new ByteArrayInputStream("data".getBytes());

        SourceOpenResult result = SourceOpenResult.success(stream, "text/plain", 4L, true);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStream()).isSameAs(stream);
        assertThat(result.getContentType()).isEqualTo("text/plain");
        assertThat(result.getContentLength()).isEqualTo(4L);
        assertThat(result.isFinite()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("success() preserves finite=false for streaming sources")
    void success_withInfiniteStream_setsFiniteFalse() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);

        SourceOpenResult result = SourceOpenResult.success(stream, "application/octet-stream", null, false);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFinite()).isFalse();
        assertThat(result.getContentLength()).isNull();
    }

    @Test
    @DisplayName("success() allows null contentLength")
    void success_withNullContentLength_isAccepted() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);

        SourceOpenResult result = SourceOpenResult.success(stream, "text/plain", null, true);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContentLength()).isNull();
    }

    @Test
    @DisplayName("failure() sets success to false and stores errorMessage")
    void failure_setsSuccessFalseAndErrorMessage() {
        SourceOpenResult result = SourceOpenResult.failure("S3 connection refused");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("S3 connection refused");
        assertThat(result.getStream()).isNull();
        assertThat(result.getContentType()).isNull();
        assertThat(result.getContentLength()).isNull();
    }

    @Test
    @DisplayName("failure() and success() are independent instances")
    void success_andFailure_returnIndependentInstances() {
        SourceOpenResult success = SourceOpenResult.success(
                new ByteArrayInputStream(new byte[0]), "text/plain", 0L, true);
        SourceOpenResult failure = SourceOpenResult.failure("error");

        assertThat(success).isNotSameAs(failure);
        assertThat(success.isSuccess()).isTrue();
        assertThat(failure.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("close() delegates to the underlying stream")
    void close_successResult_closesStream() throws IOException {
        TrackingInputStream trackingStream = new TrackingInputStream();
        SourceOpenResult result = SourceOpenResult.success(trackingStream, "text/plain", 4L, true);

        result.close();

        assertThat(trackingStream.closed).isTrue();
    }

    @Test
    @DisplayName("close() on a failure result (null stream) is a safe no-op")
    void close_failureResult_doesNotThrow() {
        SourceOpenResult result = SourceOpenResult.failure("upstream error");

        assertThatNoException().isThrownBy(result::close);
    }

    @Test
    @DisplayName("close() propagates IOException from the underlying stream")
    void close_streamThrows_propagatesIOException() {
        SourceOpenResult result = SourceOpenResult.success(new FailingInputStream(), "text/plain", 4L, true);

        assertThatThrownBy(result::close)
                .isInstanceOf(IOException.class)
                .hasMessage("close failed");
    }

    @Test
    @DisplayName("SourceOpenResult is usable in try-with-resources")
    void close_tryWithResources_streamIsClosed() throws IOException {
        TrackingInputStream trackingStream = new TrackingInputStream();

        try (SourceOpenResult result = SourceOpenResult.success(trackingStream, "application/octet-stream", null, false)) {
            assertThat(result.isSuccess()).isTrue();
        }

        assertThat(trackingStream.closed).isTrue();
    }
}
