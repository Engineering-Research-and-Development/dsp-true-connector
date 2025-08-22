package it.eng.connector.integration.datatransfer;

import it.eng.catalog.repository.DatasetRepository;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.util.ToolsUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataTransferPushIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @Autowired
    private S3ClientService s3ClientService;

    @Autowired
    private S3Properties s3Properties;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private PolicyEnforcementRepository policyEnforcementRepository;

    @Test
    @DisplayName("Push transfer process - success")
    @WithUserDetails(TestUtil.API_USER)
    public void pushTransferProcess_success() throws Exception {

        Thread.sleep(5000);
//        temporary test to verify that the presigned PUT URL generation works
        int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

        String presignedUrl = s3ClientService.generatePresignedPUTUrl(s3Properties.getBucketName(), ToolsUtil.generateUniqueId(), Duration.ofMinutes(10));

        // Create sample content
        byte[] content = "Test content".getBytes();

        // Open connection to presigned URL
        URL url = new URL(presignedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("Content-Length", String.valueOf(content.length));

        // Send the content
        connection.getOutputStream().write(content);
        connection.getOutputStream().flush();

        System.out.println(" message:" + connection.getResponseMessage());
        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());

        Thread.sleep(2000); // Wait for S3 to process the upload

        assertEquals(startingBucketFileCount + 1, s3ClientService.listFiles(s3Properties.getBucketName()).size(),
                "File count in S3 bucket should increase by 1 after successful upload");
    }


}
