package it.eng.negotiation.event;

/**
 * Fired by the Consumer after storing AGREED state, signalling that a
 * ContractAgreementVerificationMessage should be sent automatically.
 *
 * @param contractNegotiationId the internal MongoDB id of the ContractNegotiation
 */
public record AutoNegotiationVerifyEvent(String contractNegotiationId) {}

