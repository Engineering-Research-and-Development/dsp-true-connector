package it.eng.datatransfer.event;

/**
 * Fired by the Consumer after storing STARTED state, signalling that a
 * data download should be triggered automatically (HTTP_PULL only).
 *
 * @param transferProcessId the internal MongoDB id of the TransferProcess
 */
public record AutoTransferDownloadEvent(String transferProcessId) {}

