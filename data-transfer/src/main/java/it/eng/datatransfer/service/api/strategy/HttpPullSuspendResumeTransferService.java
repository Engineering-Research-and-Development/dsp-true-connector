package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.datatransfer.ResumeDataTransfer;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class HttpPullSuspendResumeTransferService {

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

    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL Suspend/Resume transfer for process {}", transferProcess.getId());

        return downloadAndUploadToS3(transferProcess)
                .thenAccept(key -> {
                    log.info("Stored transfer process id - {} data!", key);
                    transferProcessRepository.save(transferProcess.copyWithNewTransferState(TransferState.COMPLETED));
                    auditEventPublisher.publishEvent(AuditEventType.TRANSFER_COMPLETED,
                            "Data transfer completed for process " + transferProcess.getId(),
                            Map.of("role", IConstants.ROLE_PROTOCOL,
                                    "transferProcess", transferProcess,
                                    "consumerPid", transferProcess.getConsumerPid(),
                                    "providerPid", transferProcess.getProviderPid()));
                });
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

        return CompletableFuture.completedFuture("Download started");
    }


    @EventListener
    public void onSuspendTransfer(TransferSuspensionMessage transferSuspensionMessage) {
        TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(transferSuspensionMessage.getConsumerPid(), transferSuspensionMessage.getProviderPid())
                .orElseThrow(() -> new DataTransferAPIException("Transfer process not found for consumerPid: " + transferSuspensionMessage.getConsumerPid()
                        + " and providerPid: " + transferSuspensionMessage.getProviderPid()));
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
        }

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
}
