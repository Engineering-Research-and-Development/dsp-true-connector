package it.eng.dataplane.api.model;

import lombok.Getter;
import java.util.Map;

/** Result of a Data Plane transfer execution. */
@Getter
public class DataFlowResult {

    private final String dataFlowId;
    private final boolean success;
    private final String errorMessage;
    private final Map<String, String> dataAddress;

    private DataFlowResult(String dataFlowId, boolean success, String errorMessage,
                           Map<String, String> dataAddress) {
        this.dataFlowId = dataFlowId;
        this.success = success;
        this.errorMessage = errorMessage;
        this.dataAddress = dataAddress;
    }

    /**
     * Factory for a successful transfer result.
     *
     * @param dataFlowId id of the completed data flow
     * @return success result
     */
    public static DataFlowResult success(String dataFlowId) {
        return new DataFlowResult(dataFlowId, true, null, null);
    }

    /**
     * Factory for a successful transfer result with data address (e.g. presigned URL for PULL).
     *
     * @param dataFlowId id of the completed data flow
     * @param dataAddress data address map
     * @return success result
     */
    public static DataFlowResult success(String dataFlowId, Map<String, String> dataAddress) {
        return new DataFlowResult(dataFlowId, true, null, dataAddress);
    }

    /**
     * Factory for a failed transfer result.
     *
     * @param dataFlowId id of the failed data flow
     * @param errorMessage error description
     * @return failure result
     */
    public static DataFlowResult failure(String dataFlowId, String errorMessage) {
        return new DataFlowResult(dataFlowId, false, errorMessage, null);
    }
}
