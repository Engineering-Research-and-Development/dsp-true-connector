package it.eng.dataplane.api.model;

import lombok.Getter;

/** Result of a Data Plane transfer execution. */
@Getter
public class DataFlowResult {

    private boolean success;
    private boolean paused;
    private String errorMessage;

    private DataFlowResult() {}

    /**
     * Creates a successful result.
     *
     * @return successful DataFlowResult
     */
    public static DataFlowResult success() {
        DataFlowResult result = new DataFlowResult();
        result.success = true;
        return result;
    }

    /**
     * Creates a paused result indicating the upload was suspended cleanly.
     *
     * <p>A paused result is neither a success nor a failure: the transfer
     * is still alive and can be resumed.  No Control Plane completion or
     * error callback should be sent for a paused result.</p>
     *
     * @return paused DataFlowResult
     */
    public static DataFlowResult paused() {
        DataFlowResult result = new DataFlowResult();
        result.paused = true;
        return result;
    }

    /**
     * Creates a failure result with an error message.
     *
     * @param errorMessage description of the failure
     * @return failed DataFlowResult
     */
    public static DataFlowResult failure(String errorMessage) {
        DataFlowResult result = new DataFlowResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }
}
