package it.eng.connector.integration.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.s3.service.BucketCredentials;
import it.eng.tools.s3.service.S3BucketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class S3BucketIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private S3BucketService s3BucketService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithUserDetails(TestUtil.ADMIN_USER)
    public void testCreateBucketUsingS3Service() throws Exception {
        // Arrange: Prepare the bucket name and mock request
        String bucketName = "test-bucket";

        // Act: Call the S3Controller's createBucket endpoint
        var response = mockMvc.perform(
                        post("/api/s3/bucket")
                                .param("bucketName", bucketName)
                                .contentType("application/json"))
                .andReturn().getResponse();

        // Assert: Check that the response status is 200 (Ok)
        assertEquals(200, response.getStatus());
        var bucketCredentials = objectMapper.readValue(response.getContentAsString(), BucketCredentials.class);
        assertNotNull(bucketCredentials, "Bucket credentials should not be null");
        assertNotNull(bucketCredentials.accessKey(), "AccessKey should not be null");
        assertNotNull(bucketCredentials.secretKey(), "SecretKey should match the requested name");
        System.out.println("Bucket created with AccessKey: " + bucketCredentials.accessKey() +
                ", SecretKey: " + bucketCredentials.secretKey());

    }
}
