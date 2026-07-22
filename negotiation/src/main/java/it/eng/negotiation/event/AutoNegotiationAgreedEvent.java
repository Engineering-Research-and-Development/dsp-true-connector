package it.eng.negotiation.event;

/**
 * Fired by the Provider after storing REQUESTED or ACCEPTED state, signalling that a
 * ContractAgreementMessage should be sent automatically.
 *
 * @param contractNegotiationId the internal MongoDB id of the ContractNegotiation
 */
public record AutoNegotiationAgreedEvent(String contractNegotiationId) {}

