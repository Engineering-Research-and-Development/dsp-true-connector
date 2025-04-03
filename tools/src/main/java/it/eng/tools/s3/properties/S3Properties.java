package it.eng.tools.s3.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

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
}
