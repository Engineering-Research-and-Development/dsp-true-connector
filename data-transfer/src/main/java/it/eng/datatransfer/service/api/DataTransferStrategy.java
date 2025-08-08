package it.eng.datatransfer.service.api;

import it.eng.datatransfer.model.TransferProcess;

import java.util.concurrent.CompletableFuture;

public interface DataTransferStrategy {
    CompletableFuture<Void> transfer(TransferProcess transferProcess);
}
