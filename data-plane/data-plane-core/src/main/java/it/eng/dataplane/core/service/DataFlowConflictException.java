package it.eng.dataplane.core.service;

/**
 * Thrown when a {@code start} request arrives for a {@link it.eng.dataplane.core.model.DataFlowEntity}
 * that is in a lifecycle state incompatible with re-starting (e.g. STARTED, COMPLETED, or TERMINATED).
 *
 * <p>Unlike the plain {@link IllegalStateException} — which covers transient, in-flight duplicate
 * start requests that reach the DP during the narrow STARTING window — this exception signals a
 * genuine lifecycle conflict that callers should surface as an error (e.g. HTTP 409 Conflict)
 * rather than treating as an idempotent OK.</p>
 */
public class DataFlowConflictException extends IllegalStateException {

    /**
     * Constructs a new {@code DataFlowConflictException} with the specified detail message.
     *
     * @param message the detail message
     */
    public DataFlowConflictException(String message) {
        super(message);
    }
}
