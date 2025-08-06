package it.eng.connector.integration.s3;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.repository.TransferStateRepository;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.PresignedBucketDownloader;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@SpringBootTest
@Disabled("Disabled for manual testing purposes. Enable only when needed.")
public class PartDownloaderTest {

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

    String sourceFileName = "s3.md";
    String destinationFileName = "bigFile.zip";
    String fileContent = "Hello, World!";

    @Test
    public void createBuckets() {
        BucketCredentialsEntity sourceBucketCredentialsEntity = s3BucketProvisionService.createSecureBucket("dsp-true-connector-source");
        BucketCredentialsEntity destinationBucketCredentialsEntity = s3BucketProvisionService.createSecureBucket("dsp-true-connector-destination");
    }

    @Test
    public void testPartDownloader() throws InterruptedException {
        BucketCredentialsEntity sourceBucketCredentialsEntity = bucketCredentialsService.getBucketCredentials("dsp-true-connector-source");
        BucketCredentialsEntity destinationBucketCredentialsEntity = bucketCredentialsService.getBucketCredentials("dsp-true-connector-destination");

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
            System.out.println("Error uploading file: " + e.getMessage());
        }

        String presignURL = s3ClientService.generateGetPresignedUrl(sourceBucketCredentialsEntity.getBucketName(),
                "minikube-installer.exe", Duration.ofDays(1));

        System.out.println(presignURL);

        S3ClientRequest s3ClientRequest = S3ClientRequest.from(s3Properties.getRegion(),
                null,
                destinationBucketCredentialsEntity);
        S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);

        PresignedBucketDownloader downloader = new PresignedBucketDownloader(stateRepository,
                s3AsyncClient,
                httpClient,
                "test-transfer-id-big_file.zip",
                presignURL,
                destinationBucketCredentialsEntity.getBucketName(),
                destinationFileName,
                destinationFileName);

        System.out.println("Starting download...");
        downloader.run();
    }

    private String bucketEntityToString(BucketCredentialsEntity entity) {
        return String.format("BucketCredentialsEntity{id='%s', bucketName='%s', accessKey='%s', secretKey='%s' }",
                entity.getBucketName(), entity.getBucketName(), entity.getAccessKey(), entity.getSecretKey());
    }
}
