package it.eng.tools.s3.service;

public record BucketCredentials(String accessKey, String secretKey, String bucketName) {
}
