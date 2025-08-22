package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.BucketCredentialsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class HttpPushTransferStrategy extends DataTransferStrategy {

    private static final int CHUNK_SIZE = 50 * 1024 * 1024; // 50MB chunks

    private final S3ClientProvider s3ClientProvider;
    private final S3Properties s3Properties;
    private final HttpClient httpClient;
    private final BucketCredentialsService bucketCredentialsService;

    public HttpPushTransferStrategy(S3ClientProvider s3ClientProvider, S3Properties s3Properties, BucketCredentialsService bucketCredentialsService) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3Properties = s3Properties;
        this.bucketCredentialsService = bucketCredentialsService;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PUSH transfer for process {}", transferProcess.getId());
        String objectKey = transferProcess.getDatasetId();
        String destinationUrl = transferProcess.getDataAddress().getEndpoint();
        BucketCredentialsEntity bucketCredentials = bucketCredentialsService.getBucketCredentials(s3Properties.getBucketName());
        S3ClientRequest s3ClientRequest = S3ClientRequest.from(s3Properties.getRegion(),
                null,
                bucketCredentials);

        return CompletableFuture.runAsync(() -> {
            try (S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest)) {
                // Get object metadata first to determine size
                HeadObjectResponse objectInfo = s3AsyncClient.headObject(HeadObjectRequest.builder()
                        .bucket(s3Properties.getBucketName())
                        .key(objectKey)
                        .build()).join();

                long objectSize = objectInfo.contentLength();
                int numParts = (int) Math.ceil((double) objectSize / CHUNK_SIZE);
                List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();

                // Process each chunk in parallel
                for (int i = 0; i < numParts; i++) {
                    final int partNumber = i;
                    long start = (long) i * CHUNK_SIZE;
                    long end = Math.min(start + CHUNK_SIZE - 1, objectSize - 1);

                    CompletableFuture<Void> uploadFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            // Download chunk from source S3
                            GetObjectRequest getRequest = GetObjectRequest.builder()
                                    .bucket(s3Properties.getBucketName())
                                    .key(objectKey)
                                    .range("bytes=" + start + "-" + end)
                                    .build();

                            byte[] chunkData = s3AsyncClient.getObject(getRequest, AsyncResponseTransformer.toBytes())
                                    .join()
                                    .asByteArray();

                            // Upload chunk using presigned URL
                            HttpRequest putRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(destinationUrl))
                                    .header("Content-Range", "bytes " + start + "-" + end + "/" + objectSize)
                                    .PUT(HttpRequest.BodyPublishers.ofByteArray(chunkData))
                                    .build();

                            HttpResponse<Void> response = httpClient.send(putRequest,
                                    HttpResponse.BodyHandlers.discarding());
                            if (response.statusCode() != 200) {
                                throw new RuntimeException("Failed to upload chunk " + partNumber +
                                        ": " + response.statusCode());
                            }
                            log.info("Successfully uploaded chunk {} of {}, for transfer process {}", partNumber + 1, numParts, transferProcess.getId());
                            return null;
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException("Error processing chunk " + partNumber, e);
                        }
                    });

                    uploadFutures.add(uploadFuture);
                }

                // Wait for all uploads to complete
                CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
                log.info("Successfully completed transfer for process {}", transferProcess.getId());

            } catch (Exception e) {
                log.error("Error during transfer process {}", transferProcess.getId(), e);
                throw new RuntimeException("Transfer failed", e);
            }
        });
    }
}
