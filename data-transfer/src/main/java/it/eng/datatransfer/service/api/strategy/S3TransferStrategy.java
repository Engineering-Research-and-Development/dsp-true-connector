package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.s3.service.S3ClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class S3TransferStrategy implements DataTransferStrategy {

    private S3ClientService s3ClientService;
//    String bucketName

    @Override
    public CompletableFuture<String> transfer(TransferProcess transferProcess) {
        log.info("Executing S3 to S3 transfer for process {}", transferProcess.getId());
        // Implementation will go here
        return CompletableFuture.failedFuture(new UnsupportedOperationException("S3 transfer not implemented"));
    }

    @Override
    public CompletableFuture<String> suspendTransfer(TransferProcess transferProcess) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("S3 transfer not implemented"));
    }

    @Override
    public CompletableFuture<String> terminateTransfer(TransferProcess transferProcess) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("S3 transfer not implemented"));
    }
}
