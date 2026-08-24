package it.eng.dataplane.api.io;

import lombok.Getter;

/**
 * Result of writing data to a sink.
 */
@Getter
public class SinkWriteResult {

    private boolean success;
    private String objectIdentifier;
    private String errorMessage;

    private SinkWriteResult() {
    }

    /**
     * Creates a successful sink-write result.
     *
     * @param objectIdentifier sink-specific object identifier
     * @return successful result
     */
    public static SinkWriteResult success(String objectIdentifier) {
        SinkWriteResult result = new SinkWriteResult();
        result.success = true;
        result.objectIdentifier = objectIdentifier;
        return result;
    }

    /**
     * Creates a failed sink-write result.
     *
     * @param errorMessage failure description
     * @return failed result
     */
    public static SinkWriteResult failure(String errorMessage) {
        SinkWriteResult result = new SinkWriteResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }
}
