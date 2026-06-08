package it.eng.dataplane.core.service;

import it.eng.dataplane.api.model.DataFlowResult;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link DataFlowExecutionHandle} backed by a {@link CompletableFuture}.
 *
 * <p>Cancelling this handle requests cancellation of the underlying future, which
 * propagates a {@link java.util.concurrent.CancellationException} to any chained callbacks.</p>
 */
class FutureDataFlowExecutionHandle implements DataFlowExecutionHandle {

    private final String processId;
    private final CompletableFuture<DataFlowResult> future;

    /**
     * Constructs a handle wrapping the given transfer future.
     *
     * @param processId the DSP transfer process ID
     * @param future    the in-progress transfer future
     */
    FutureDataFlowExecutionHandle(String processId, CompletableFuture<DataFlowResult> future) {
        this.processId = processId;
        this.future = future;
    }

    @Override
    public String getProcessId() {
        return processId;
    }

    @Override
    public void cancel() {
        future.cancel(true);
    }
}
