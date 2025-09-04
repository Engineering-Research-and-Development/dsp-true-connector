package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.datatransfer.ResumeDataTransfer;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.model.TransferArtifactState;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.repository.TransferStateRepository;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.PresignedBucketDownloader;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class HttpPullSuspendResumeTransferService implements DataTransferStrategy {

    private final ConcurrentHashMap<String, PresignedBucketDownloader> downloaders = new ConcurrentHashMap<>();

    private final AuditEventPublisher auditEventPublisher;
    private final BucketCredentialsService bucketCredentialsService;
    private final S3Properties s3Properties;
    private final S3ClientProvider s3ClientProvider;
    private final OkHttpClient httpClient;
    private final TransferStateRepository stateRepository;
    private final TransferProcessRepository transferProcessRepository;

    public HttpPullSuspendResumeTransferService(AuditEventPublisher auditEventPublisher, BucketCredentialsService bucketCredentialsService,
                                                S3Properties s3Properties,
                                                S3ClientProvider s3ClientProvider,
                                                OkHttpClient httpClient,
                                                TransferStateRepository stateRepository,
                                                TransferProcessRepository transferProcessRepository) {
        this.auditEventPublisher = auditEventPublisher;
        this.bucketCredentialsService = bucketCredentialsService;
        this.s3Properties = s3Properties;
        this.s3ClientProvider = s3ClientProvider;
        this.httpClient = httpClient;
        this.stateRepository = stateRepository;
        this.transferProcessRepository = transferProcessRepository;
    }

    @Override
    public CompletableFuture<String> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL Suspend/Resume transfer for process {}", transferProcess.getId());

        return downloadAndUploadToS3(transferProcess)
                .whenComplete((transferProcessId, throwable) -> {
                    if (throwable == null) {
                        log.info("-------------- Stored transfer process id - {} data!", transferProcessId);
                        auditEventPublisher.publishEvent(AuditEventType.TRANSFER_COMPLETED,
                                "Data transfer completed for process " + transferProcess.getId(),
                                Map.of("role", IConstants.ROLE_PROTOCOL,
                                        "transferProcess", transferProcess,
                                        "consumerPid", transferProcess.getConsumerPid(),
                                        "providerPid", transferProcess.getProviderPid()));
                    } else {
                        log.error("-------------- Error during transfer process id - {} data: {}", transferProcessId, throwable.getMessage());
                        auditEventPublisher.publishEvent(AuditEventType.TRANSFER_FAILED,
                                "Data transfer failed for process " + transferProcess.getId() + ": " + throwable.getMessage(),
                                Map.of("role", IConstants.ROLE_PROTOCOL,
                                        "transferProcess", transferProcess,
                                        "consumerPid", transferProcess.getConsumerPid(),
                                        "providerPid", transferProcess.getProviderPid()));
                    }
                });
    }

    @Override
    public CompletableFuture<String> suspendTransfer(TransferProcess transferProcess) {
//        TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(transferSuspensionMessage.getConsumerPid(), transferSuspensionMessage.getProviderPid())
//                .orElseThrow(() -> new DataTransferAPIException("Transfer process not found for consumerPid: " + transferSuspensionMessage.getConsumerPid()
//                        + " and providerPid: " + transferSuspensionMessage.getProviderPid()));
        log.info("Suspending transfer process {}", transferProcess.getId());
        if (transferProcess.getState().equals(TransferState.STARTED)
                && transferProcess.getRole().equals(IConstants.ROLE_CONSUMER)
                && !transferProcess.isDownloaded()) {
            PresignedBucketDownloader downloader = downloaders.get(transferProcess.getId());
            if (downloader != null) {
                downloader.pause();
            } else {
                log.warn("No downloader found for transfer process {}", transferProcess.getId());
            }
        } else {
            log.warn("Transfer process {} is not in STARTED state or not a CONSUMER, cannot suspend", transferProcess.getId());
            return CompletableFuture.failedFuture(new DataTransferAPIException("Transfer process " + transferProcess.getId() + " is not in STARTED state"));
        }
        return CompletableFuture.completedFuture(transferProcess.getId());
    }

    @Override
    public CompletableFuture<String> terminateTransfer(TransferProcess transferProcess) {
        PresignedBucketDownloader downloader = downloaders.get(transferProcess.getId());
        if (downloader != null) {
            downloader.stop();

            TransferArtifactState transferArtifactState = stateRepository.findById(transferProcess.getId())
                    .orElseThrow(() -> new DataTransferAPIException("Transfer state not found for id: " + transferProcess.getId()));

            List<String> eTags = transferArtifactState.getEtags();
            if (eTags != null && !eTags.isEmpty()) {
                // abort multipart upload
                BucketCredentialsEntity destinationBucketCredentialsEntity =
                        bucketCredentialsService.getBucketCredentials(s3Properties.getBucketName());

                S3ClientRequest s3ClientRequest = S3ClientRequest.from(s3Properties.getRegion(),
                        null,
                        destinationBucketCredentialsEntity);
                S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);

                AbortMultipartUploadRequest abortMultipartUploadRequest = AbortMultipartUploadRequest.builder()
                        .bucket(destinationBucketCredentialsEntity.getBucketName())
                        .key(transferProcess.getId())
                        .uploadId(transferArtifactState.getUploadId())
                        .build();

                s3AsyncClient.abortMultipartUpload(abortMultipartUploadRequest)
                        .whenComplete((resp, err) -> {
                            if (err != null) {
                                log.error("Failed to abort multipart upload for transfer process {}: {}", transferProcess.getId(), err.getMessage());
                            } else {
                                log.info("Aborted multipart upload for transfer process {}", transferProcess.getId());
                            }
                        });
            }
            downloaders.remove(transferProcess.getId());
            stateRepository.deleteById(transferProcess.getId());

            log.info("Terminated transfer process {}", transferProcess.getId());
            auditEventPublisher.publishEvent(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED,
                    "Data transfer terminated for process " + transferProcess.getId(),
                    Map.of("role", IConstants.ROLE_PROTOCOL,
                            "transferProcess", transferProcess,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid()));
        } else {
            log.warn("No downloader found for transfer process {}", transferProcess.getId());
        }
        return CompletableFuture.completedFuture(transferProcess.getId());
    }

    private CompletableFuture<String> downloadAndUploadToS3(TransferProcess transferProcess) {
        BucketCredentialsEntity destinationBucketCredentialsEntity =
                bucketCredentialsService.getBucketCredentials(s3Properties.getBucketName());

        S3ClientRequest s3ClientRequest = S3ClientRequest.from(s3Properties.getRegion(),
                null,
                destinationBucketCredentialsEntity);
        S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);

        PresignedBucketDownloader downloader = new PresignedBucketDownloader(stateRepository,
                s3AsyncClient,
                httpClient,
                transferProcess.getId(),
                transferProcess.getDataAddress().getEndpoint(),
                destinationBucketCredentialsEntity.getBucketName(),
                transferProcess.getId(),
                transferProcess.getId());

        log.info("Starting download...");
        downloader.run();
        downloaders.put(transferProcess.getId(), downloader);

        return CompletableFuture.completedFuture(transferProcess.getId());
    }


    @EventListener
    public void onResume(ResumeDataTransfer resumeDataTransfer) {
        log.info("Resuming transfer process {}", resumeDataTransfer.getTransferProcessId());
        TransferProcess transferProcess = transferProcessRepository.findById(resumeDataTransfer.getTransferProcessId())
                .orElseThrow(() -> new DataTransferAPIException("Transfer process not found for id: " + resumeDataTransfer.getTransferProcessId()));
        if (transferProcess.getState().equals(TransferState.SUSPENDED)
                && transferProcess.getRole().equals(IConstants.ROLE_CONSUMER)
                && !transferProcess.isDownloaded()) {
            PresignedBucketDownloader downloader = downloaders.get(resumeDataTransfer.getTransferProcessId());
            if (downloader != null) {
                downloader.resume();
            } else {
                log.warn("No downloader found for transfer process {}", resumeDataTransfer.getTransferProcessId());
            }
        } else {
            log.warn("Transfer process {} is not in SUSPENDED state or not a CONSUMER, cannot resume", transferProcess.getId());
            return;
        }
    }

    @EventListener
    public void onTransferComplete(TransferCompletionMessage transferCompletionMessage) {
        TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(transferCompletionMessage.getConsumerPid(), transferCompletionMessage.getProviderPid())
                .orElseThrow(() -> new DataTransferAPIException("Transfer process not found for consumerPid: " + transferCompletionMessage.getConsumerPid()
                        + " and providerPid: " + transferCompletionMessage.getProviderPid()));
        log.info("Cleaning up transfer process {}", transferProcess.getId());
        if (transferProcess.getRole().equals(IConstants.ROLE_CONSUMER)) {
            PresignedBucketDownloader downloader = downloaders.get(transferProcess.getId());
            if (downloader != null) {
                downloader.stop();
                downloaders.remove(transferProcess.getId());
            } else {
                log.warn("No downloader found for transfer process {}", transferProcess.getId());
            }
        }
    }

    private TransferProcess withNewDownloaded(TransferProcess oldTransferProcess, boolean isDownloaded) {
        return TransferProcess.Builder.newInstance()
                .id(oldTransferProcess.getId())
                .agreementId(oldTransferProcess.getAgreementId())
                .consumerPid(oldTransferProcess.getConsumerPid())
                .providerPid(oldTransferProcess.getProviderPid())
                .callbackAddress(oldTransferProcess.getCallbackAddress())
                .dataAddress(oldTransferProcess.getDataAddress())
                .isDownloaded(isDownloaded)
                .dataId(oldTransferProcess.getDataId())
                .format(oldTransferProcess.getFormat())
                .state(oldTransferProcess.getState())
                .role(oldTransferProcess.getRole())
                .datasetId(oldTransferProcess.getDatasetId())
                // auditable fields
                .createdBy(oldTransferProcess.getCreatedBy())
                .created(oldTransferProcess.getCreated())
                .lastModifiedBy(oldTransferProcess.getLastModifiedBy())
                .modified(oldTransferProcess.getModified())
                .version(oldTransferProcess.getVersion())
                .build();
    }
}
