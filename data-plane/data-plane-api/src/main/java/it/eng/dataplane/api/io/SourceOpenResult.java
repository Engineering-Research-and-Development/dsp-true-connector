package it.eng.dataplane.api.io;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;

/**
 * Result of opening a source stream.
 *
 * <p>Implements {@link AutoCloseable} so callers can use try-with-resources to ensure the
 * underlying stream is always released. Closing a failure result (which has no stream) is a safe
 * no-op.
 */
@Getter
public class SourceOpenResult implements AutoCloseable {

    private boolean success;
    private InputStream stream;
    private String contentType;
    private Long contentLength;
    private boolean finite;
    private String errorMessage;

    private SourceOpenResult() {
    }

    /**
     * Creates a successful source-open result.
     *
     * @param stream source stream
     * @param contentType content type
     * @param contentLength content length, may be {@code null}
     * @param finite whether the stream is finite
     * @return successful result
     */
    public static SourceOpenResult success(InputStream stream, String contentType, Long contentLength, boolean finite) {
        SourceOpenResult result = new SourceOpenResult();
        result.success = true;
        result.stream = stream;
        result.contentType = contentType;
        result.contentLength = contentLength;
        result.finite = finite;
        return result;
    }

    /**
     * Creates a failed source-open result.
     *
     * @param errorMessage failure description
     * @return failed result
     */
    public static SourceOpenResult failure(String errorMessage) {
        SourceOpenResult result = new SourceOpenResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }

    /**
     * Closes the underlying stream if one is present.
     *
     * <p>Safe to call on failure results or when the stream is {@code null} — the method
     * does nothing in those cases.
     *
     * @throws IOException if the underlying stream throws on close
     */
    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
    }
}
