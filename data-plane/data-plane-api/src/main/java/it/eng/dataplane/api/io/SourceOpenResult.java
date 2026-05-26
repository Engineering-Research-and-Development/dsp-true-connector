package it.eng.dataplane.api.io;

import lombok.Getter;

import java.io.InputStream;

/**
 * Result of opening a source stream.
 */
@Getter
public class SourceOpenResult {

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
}
