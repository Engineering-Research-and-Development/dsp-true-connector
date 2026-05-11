package it.eng.dataplane.api.spi;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;

import java.util.concurrent.CompletableFuture;

/**
 * SPI interface for data transfer protocol implementations.
 * Each data plane implementation must implement this interface.
 */
public interface DataTransferProtocol {

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
