package it.eng.datatransfer.event;

/**
 * Fired by the Provider after storing REQUESTED state, signalling that a
 * TransferStartMessage should be sent automatically.
 *
 * @param transferProcessId the internal MongoDB id of the TransferProcess
 */
public record AutoTransferStartEvent(String transferProcessId) {}

