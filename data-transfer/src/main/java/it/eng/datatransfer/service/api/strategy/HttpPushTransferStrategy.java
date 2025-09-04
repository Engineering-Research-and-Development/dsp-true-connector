package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class HttpPushTransferStrategy implements DataTransferStrategy {

    @Override
    public CompletableFuture<String> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PUSH transfer for process {}", transferProcess.getId());
        // Implementation will go here
        return CompletableFuture.failedFuture(new UnsupportedOperationException("HTTP PUSH transfer not implemented"));
    }

    @Override
    public CompletableFuture<String> suspendTransfer(TransferProcess transferProcess) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("HTTP PUSH transfer not implemented"));
    }

    @Override
    public CompletableFuture<String> terminateTransfer(TransferProcess transferProcess) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("HTTP PUSH transfer not implemented"));
    }
}
