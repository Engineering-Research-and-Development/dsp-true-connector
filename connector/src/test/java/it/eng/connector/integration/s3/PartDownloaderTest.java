package it.eng.connector.integration.s3;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.strategy.HttpPullTransferStrategy;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.repository.TransferStateRepository;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PartDownloaderTest extends BaseIntegrationTest {

    @Autowired
    TransferStateRepository stateRepository;

    @Autowired
    private BucketCredentialsService bucketCredentialsService;

    @Autowired
    private S3Properties s3Properties;

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private S3ClientProvider s3ClientProvider;

    @Autowired
    private S3ClientService s3ClientService;

    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;

    @Autowired
    private HttpPullTransferStrategy httpPullTransferStrategy;

    String sourceFileName = "s3.md";
    String destinationFileName = "bigFile.zip";
    String fileContent = "Hello, World!";
    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @Test
    public void testPartDownloader() throws InterruptedException, ExecutionException {

        BucketCredentialsEntity sourceBucketCredentialsEntity = s3BucketProvisionService.createSecureBucket("dsp-true-connector-source");
        BucketCredentialsEntity destinationBucketCredentialsEntity = s3BucketProvisionService.createSecureBucket("dsp-true-connector-destination");

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(sourceFileName)
                .build();

        String objectKey = UUID.randomUUID().toString();
        try (InputStream inputStream = new ByteArrayInputStream(fileContent.getBytes())) {
            s3ClientService.uploadFile(inputStream,
                            sourceBucketCredentialsEntity.getBucketName(),
                            objectKey,
                            MediaType.TEXT_PLAIN_VALUE,
                            contentDisposition.toString())
                    .get();
        } catch (Exception e) {
            System.out.println("Error uploading file to source bucket: " + e.getMessage());
        }

        String presignURL = s3ClientService.generateGetPresignedUrl(sourceBucketCredentialsEntity.getBucketName(),
                objectKey,
                Duration.ofDays(1));

        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(presignURL)
                .endpointType(DataTransferMockObjectUtil.ENDPOINT_TYPE)
                .build();

        TransferProcess tp = TransferProcess.Builder.newInstance()
                .isDownloaded(false)
                .id(UUID.randomUUID().toString())
                .dataId(UUID.randomUUID().toString())
                .datasetId("integration-test-dataset-1")
                .dataAddress(dataAddress)
                .state(TransferState.STARTED)
                .build();
        s3Properties.setBucketName("dsp-true-connector-destination");
        httpPullTransferStrategy.transfer(tp).whenComplete((transferProcessId, throwable) -> {
            assertNotNull(transferProcessId);

            String presignURLDestination = s3ClientService.generateGetPresignedUrl(destinationBucketCredentialsEntity.getBucketName(),
                    tp.getId(),
                    Duration.ofDays(1));
            assertNotNull(presignURLDestination);
        });
    }
}
