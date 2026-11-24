package it.eng.dcp.common.participant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an authenticated administration principal acts on a participant it is not authorized for.
 * Maps to HTTP 403.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ParticipantForbiddenException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message why the action is forbidden
     */
    public ParticipantForbiddenException(String message) {
        super(message);
    }
}
