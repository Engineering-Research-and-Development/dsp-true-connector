package it.eng.tools.s3.service;

import it.eng.tools.s3.model.TransferState;
import it.eng.tools.s3.repository.TransferStateRepository;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PresignedBucketDownloader implements Runnable {

    private final TransferStateRepository stateRepository;
    private final S3AsyncClient s3AsyncClient;
    private final OkHttpClient httpClient;

    private final String transferId;
    private final String presignedUrl;
    private final String destBucket;
    private final String destObject;
    private final String key;

    private final int partSize = 5 * 1024 * 1024;
    private volatile boolean paused = false;
    private volatile boolean stopped = false;
    private static final int MAX_RETRIES = 3;

    public PresignedBucketDownloader(TransferStateRepository stateRepository, S3AsyncClient s3AsyncClient, OkHttpClient httpClient,
                                     String transferId, String presignedUrl,
                                     String destBucket, String destObject, String key) {
        this.stateRepository = stateRepository;
        this.s3AsyncClient = s3AsyncClient;
        this.httpClient = httpClient;
        this.transferId = transferId;
        this.presignedUrl = presignedUrl;
        this.destBucket = destBucket;
        this.destObject = destObject;
        this.key = key;
    }

    @Override
    public void run() {
        TransferState state = getOrCreateState();
        int retryCount = 0;

        Request.Builder builder = new Request.Builder().url(presignedUrl);

        log.info("Starting download for transferId: {}, presignedUrl: {}, destBucket: {}, destObject: {}",
                transferId, presignedUrl, destBucket, destObject);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() && response.code() != 206) {
                throw new IOException("Download failed: " + response);
            }

            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response");

            String contentType = response.header("Content-Type");
            String contentDisposition = response.header("Content-Disposition");

            if (state.getUploadId() == null) {
                CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                        .bucket(destBucket)
                        .contentType(contentType)
                        .contentDisposition(contentDisposition)
                        .key(key)
                        .build();

                String uploadId = s3AsyncClient.createMultipartUpload(createMultipartUploadRequest)
                        .join()
                        .uploadId();

                state.setUploadId(uploadId);
                stateRepository.save(state);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            while (retryCount < MAX_RETRIES && !stopped) {
                builder = new Request.Builder().url(presignedUrl);

//                if (state.getDownloadedBytes() > 0) {
                builder.addHeader("Range", "bytes=" + state.getDownloadedBytes() + "-" +
                        (state.getDownloadedBytes() + partSize - 1));
//                }
                try (Response response = httpClient.newCall(builder.build()).execute()) {
                    ResponseBody body = response.body();
                    if (body == null) throw new IOException("Empty response");

                    // Upload part to destination bucket
                    try (InputStream in = body.byteStream()) {
                        byte[] partData = in.readAllBytes();
                        log.info("Doing part download for transferId: {}, range: bytes={}-{}",
                                transferId, state.getDownloadedBytes(), state.getDownloadedBytes() + partSize - 1);

                        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                                .bucket(destBucket)
                                .key(key)
                                .uploadId(state.getUploadId())
                                .partNumber(state.getPartNumber())
                                .build();

                        String eTag = s3AsyncClient.uploadPart(uploadPartRequest,
                                        AsyncRequestBody.fromBytes(partData))
                                .join()
                                .eTag();

                        state.getEtags().add(eTag);
                        state.incrementPartNumber();
                        state.setDownloadedBytes(state.getDownloadedBytes() + partSize);
                        stateRepository.save(state);

                        if (partData.length < partSize) {
                            log.info("Last part downloaded, size: {}", partData.length);

                            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                                    .parts(createPartsList(state.getEtags()))
                                    .build();

                            CompleteMultipartUploadRequest completeMultipartUploadRequest =
                                    CompleteMultipartUploadRequest.builder()
                                            .bucket(destBucket)
                                            .key(key)
                                            .uploadId(state.getUploadId())
                                            .multipartUpload(completedMultipartUpload)
                                            .build();

                            log.info("Completing multipart upload for key: {} with uploadId: {}", key, state.getUploadId());
                            s3AsyncClient.completeMultipartUpload(completeMultipartUploadRequest)
                                    .join();

                            // Clean up state after successful completion
                            stateRepository.deleteById(transferId);
                            //stop transfer since it is completed
                            break;
                        }

                        while (paused) {
                            synchronized (this) {
                                wait();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Download failed", e);
                    retryCount++;
                    if (retryCount < MAX_RETRIES) {
                        Thread.sleep(1000 * retryCount);
                    }
                }

            }
        } catch (Exception e) {
            log.error("Download failed", e);
        }
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
        synchronized (this) {
            notify();
        }
    }

    public void stop() {
        stopped = true;
        resume(); // Wake up if paused
    }

    private TransferState getOrCreateState() {
        TransferState state = stateRepository.findById(transferId)
                .orElse(null);
        if (state == null) {

            state = TransferState.Builder.newInstance()
                    .id(transferId)
                    .presignURL(presignedUrl)
                    .destBucket(destBucket)
                    .destObject(destObject)
                    .downloadedBytes(0)
                    .partNumber(1)
                    .build();

            state = stateRepository.save(state);
        }
        return state;
    }

    private List<CompletedPart> createPartsList(List<String> eTags) {
        List<CompletedPart> parts = new ArrayList<>();
        for (int i = 0; i < eTags.size(); i++) {
            parts.add(CompletedPart.builder()
                    .partNumber(i + 1)
                    .eTag(eTags.get(i))
                    .build());
        }
        return parts;
    }
}
