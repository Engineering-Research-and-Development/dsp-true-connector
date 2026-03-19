package it.eng.negotiation.event;

/**
 * Fired by the Consumer after storing OFFERED state (initial offer only, not counteroffer),
 * signalling that a ContractNegotiationEventMessage:accepted should be sent automatically.
 *
 * @param contractNegotiationId the internal MongoDB id of the ContractNegotiation
 */
public record AutoNegotiationAcceptedEvent(String contractNegotiationId) {}

