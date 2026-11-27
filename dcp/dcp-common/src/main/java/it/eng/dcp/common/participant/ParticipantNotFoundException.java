package it.eng.dcp.common.participant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a request addresses an unknown or disabled participant. Maps to HTTP 404, before any DCP
 * processing or token validation.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ParticipantNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what was not found; does not reveal whether the participant is disabled
     */
    public ParticipantNotFoundException(String message) {
        super(message);
    }
}
