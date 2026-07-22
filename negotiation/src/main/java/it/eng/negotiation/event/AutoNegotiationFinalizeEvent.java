package it.eng.negotiation.event;

/**
 * Fired by the Provider after storing VERIFIED state, signalling that a
 * ContractNegotiationEventMessage:finalized should be sent automatically.
 *
 * @param contractNegotiationId the internal MongoDB id of the ContractNegotiation
 */
public record AutoNegotiationFinalizeEvent(String contractNegotiationId) {}

