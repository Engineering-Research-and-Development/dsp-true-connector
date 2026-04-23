package it.eng.datatransfer.exceptions;

import java.io.Serial;

/**
 * Thrown when a running transfer is stopped by a suspension signal via {@code CancellationRegistry}.
 */
public class TransferCancelledException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception for the given transfer process.
     *
     * @param transferProcessId the MongoDB ID of the suspended TransferProcess
     */
    public TransferCancelledException(String transferProcessId) {
        super("Transfer " + transferProcessId + " was stopped by a suspension signal.");
    }
}
