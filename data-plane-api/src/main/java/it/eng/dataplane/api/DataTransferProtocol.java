package it.eng.dataplane.api;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import java.util.concurrent.CompletableFuture;

/**
 * SPI for Data Plane transfer protocol implementations.
 * Each implementation handles one transfer type (e.g. "HttpData-PULL").
 * Implementations are Spring beans discovered by {@code DataTransferProtocolRegistry}.
 */
public interface DataTransferProtocol {

    /**
     * Returns the transfer type string this implementation handles (e.g. "HttpData-PULL").
     *
     * @return transfer type identifier
     */
    String transferType();

    /**
     * Executes the data transfer asynchronously.
     * Implementations must call back the Control Plane via ControlPlaneClient on completion or failure.
     *
     * @param dataFlow the data flow to execute
     * @return future completing with the transfer result
     */
    CompletableFuture<DataFlowResult> execute(DataFlow dataFlow);

    /**
     * Suspends an in-progress transfer.
     *
     * @param dataFlow the data flow to suspend
     */
    void suspend(DataFlow dataFlow);

    /**
     * Resumes a suspended transfer.
     *
     * @param dataFlow the data flow to resume
     */
    void resume(DataFlow dataFlow);

    /**
     * Terminates a transfer, releasing all resources.
     *
     * @param dataFlow the data flow to terminate
     */
    void terminate(DataFlow dataFlow);
}
