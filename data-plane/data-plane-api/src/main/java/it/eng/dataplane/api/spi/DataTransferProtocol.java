package it.eng.dataplane.api.spi;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;

import java.util.concurrent.CompletableFuture;

/**
 * SPI interface for data transfer protocol implementations.
 * Each data plane implementation must implement this interface.
 */
public interface DataTransferProtocol {

    /**
     * Prepares resources for a data transfer before the DSP protocol messages are exchanged.
     *
     * <p>For HTTP-PULL (provider side): generates a pre-signed GET URL for the artifact
     * and returns it as {@code dataAddress.presignedUrl}.</p>
     *
     * <p>For HTTP-PUSH (consumer side): creates a temporary IAM user for the consumer bucket
     * and returns the credentials in {@code dataAddress} so the Control Plane can embed them
     * in the outgoing {@code TransferRequestMessage}.</p>
     *
     * <p>Default implementation returns an empty response. Override in protocol implementations
     * that need resource allocation before the transfer starts.</p>
     *
     * @param message the prepare message from the Control Plane
     * @return the prepare response carrying protocol-specific addressing data
     */
    default DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
        return DataFlowPrepareResponse.Builder.newInstance()
                .processId(message.getProcessId())
                .build();
    }

    /**
     * Returns the unique identifier for this transfer protocol.
     *
     * @return protocol identifier string
     */
    String getProtocolId();

    /**
     * Initiates a data transfer for the given data flow.
     *
     * @param dataFlow the data flow to initiate
     * @return future with the result of the transfer initiation
     */
    CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow);

    /**
     * Suspends an active data transfer.
     *
     * @param dataFlowId the ID of the data flow to suspend
     * @return future with the result of the suspension
     */
    CompletableFuture<DataFlowResult> suspendTransfer(String dataFlowId);

    /**
     * Resumes a suspended data transfer.
     *
     * @param dataFlowId the ID of the data flow to resume
     * @return future with the result of the resumption
     */
    CompletableFuture<DataFlowResult> resumeTransfer(String dataFlowId);

    /**
     * Terminates a data transfer.
     *
     * @param dataFlowId the ID of the data flow to terminate
     * @return future with the result of the termination
     */
    CompletableFuture<DataFlowResult> terminateTransfer(String dataFlowId);
}
