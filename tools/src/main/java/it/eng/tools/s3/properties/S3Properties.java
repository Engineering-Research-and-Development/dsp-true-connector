package it.eng.tools.s3.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "s3")
public class S3Properties {
    
    /**
     * The S3 endpoint URL.
     */
    private String endpoint;
    
    /**
     * The S3 access key.
     */
    private String accessKey;
    
    /**
     * The S3 secret key.
     */
    private String secretKey;
    
    /**
     * The S3 region.
     */
    private String region;
    
    /**
     * The S3 bucket name.
     */
    private String bucketName;

    /**
     * The S3 external presigned endpoint.
     * This is used for generating presigned URLs for external access.
     */
    private String externalPresignedEndpoint;

    /**
     * The S3 upload mode (SYNC or ASYNC).
     * ASYNC uses S3AsyncClient for parallel uploads (faster).
     * SYNC uses S3Client for sequential uploads (more compatible with reverse proxies).
     * Defaults to SYNC if not specified.
     */
    private String uploadMode = "SYNC";

    /**
     * The chunk size in bytes for multipart uploads.
     * Defaults to 10485760 (10 MB) if not specified.
     */
    private int chunkSize = 10 * 1024 * 1024;
}
