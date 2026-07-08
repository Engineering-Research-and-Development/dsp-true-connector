package it.eng.tools.exceptions;

/**
 * Thrown when a running transfer is stopped by a suspension signal via {@code CancellationRegistry}.
 */
public class TransferCancelledException extends RuntimeException {

    private static final long serialVersionUID = -3918462109287541872L;

    /**
     * Constructs a new exception for the given transfer process.
     *
     * @param transferProcessId the MongoDB ID of the suspended TransferProcess
     */
    public TransferCancelledException(String transferProcessId) {
        super("Transfer " + transferProcessId + " was stopped by a suspension signal.");
    }
}
