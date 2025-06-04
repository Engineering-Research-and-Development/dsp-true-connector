package it.eng.tools.s3.rest;

import it.eng.tools.s3.service.BucketCredentials;
import it.eng.tools.s3.service.S3BucketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/s3")
public class S3Controller {

    private final S3BucketService s3BucketService;

    public S3Controller(S3BucketService s3BucketService) {
        this.s3BucketService = s3BucketService;
    }

    @PostMapping("/bucket")
    public ResponseEntity<BucketCredentials> createBucket(@RequestParam String bucketName) {
        BucketCredentials credentials = s3BucketService.createSecureBucket(bucketName);
        return ResponseEntity.ok(credentials);
    }

    @GetMapping("/presigned")
    public ResponseEntity<String> getPresignedUrl(@RequestParam String bucketName,
                                                  @RequestParam String objectKey,
                                                  @RequestParam String accessKey,
                                                  @RequestParam String secretKey) {
        String url = s3BucketService.generatePresignedUrl(bucketName, objectKey, Duration.ofMinutes(60));
        return ResponseEntity.ok(url);
    }

    @DeleteMapping("/bucket")
    public ResponseEntity<Void> cleanupBucket(@RequestParam String bucketName) {
        s3BucketService.cleanupBucket(bucketName);
        return ResponseEntity.ok().build();
    }
}
