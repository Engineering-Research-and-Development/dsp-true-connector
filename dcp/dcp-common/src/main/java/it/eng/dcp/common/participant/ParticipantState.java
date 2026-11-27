package it.eng.dcp.common.participant;

/**
 * Lifecycle state of a participant context. A {@link #DISABLED} participant is never usable.
 */
public enum ParticipantState {
    /** The participant can be served. */
    ENABLED,
    /** The participant exists but must not be served or used for signing. */
    DISABLED
}
