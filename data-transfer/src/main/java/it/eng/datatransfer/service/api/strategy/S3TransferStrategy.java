package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Placeholder for a future S3-to-S3 transfer strategy.
 * <p>
 * <strong>NOT IMPLEMENTED.</strong> This strategy always fails with
 * {@link UnsupportedOperationException}. It must not be enabled until a full
 * implementation is provided.
 * </p>
 */
@Service
@Slf4j
public class S3TransferStrategy {

    /**
     * Not implemented. Always returns a failed future.
     *
     * @param transferProcess the transfer process (unused)
     * @return a failed {@link CompletableFuture} with {@link UnsupportedOperationException}
     */
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        log.warn("S3TransferStrategy is not implemented and must not be used. Transfer process id: {}", transferProcess.getId());
        return CompletableFuture.failedFuture(new UnsupportedOperationException("S3-to-S3 transfer is not yet implemented"));
    }
}
