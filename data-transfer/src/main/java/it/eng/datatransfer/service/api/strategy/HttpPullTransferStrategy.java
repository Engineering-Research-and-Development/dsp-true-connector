package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class HttpPullTransferStrategy implements DataTransferStrategy {

    private final HttpPullTransferService httpPullTransferService;
    private final HttpPullSuspendResumeTransferService httpPullSuspendResumeTransferService;
    private final OkHttpClient okHttpClient;

    public HttpPullTransferStrategy(HttpPullTransferService httpPullTransferService,
                                    HttpPullSuspendResumeTransferService httpPullSuspendResumeTransferService, OkHttpClient okHttpClient) {
        this.httpPullTransferService = httpPullTransferService;
        this.httpPullSuspendResumeTransferService = httpPullSuspendResumeTransferService;
        this.okHttpClient = okHttpClient;
    }


    @Override
    public CompletableFuture<String> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL transfer for process {}", transferProcess.getId());

        Request request = new Request.Builder()
                .url(transferProcess.getDataAddress().getEndpoint())
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (Objects.equals(response.header("Accept-Ranges"), "bytes")) {
                    // Handle suspend/resume transfer
                    return httpPullSuspendResumeTransferService.transfer(transferProcess);
                } else {
                    // Handle standard transfer
                    return httpPullTransferService.transfer(transferProcess);
                }
            } else {
                log.error("Failed to fetch data from endpoint: {}. Response code: {}", transferProcess.getDataAddress().getEndpoint(), response.code());
                throw new DataTransferAPIException("Failed to fetch data from endpoint: " + transferProcess.getDataAddress().getEndpoint());
            }
        } catch (IOException e) {
            log.error("Error while executing HTTP PULL transfer for process {}", transferProcess.getId(), e);
            throw new DataTransferAPIException("Error while executing HTTP PULL transfer for process " + transferProcess.getId(), e);
        }
    }

    @Override
    public CompletableFuture<String> suspendTransfer(TransferProcess transferProcess) {
        return CompletableFuture.failedFuture(new DataTransferAPIException("Not supported operation"));
    }

    @Override
    public CompletableFuture<String> terminateTransfer(TransferProcess transferProcess) {
        // for now just delegate to the suspend/resume service
        return httpPullSuspendResumeTransferService.terminateTransfer(transferProcess);
    }
}
