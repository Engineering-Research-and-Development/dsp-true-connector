package it.eng.tools.s3.model;

import it.eng.tools.s3.service.BucketCredentials;

public record S3ClientRequest(String region, String endpointOverride, BucketCredentials bucketCredentials) {

    public static S3ClientRequest from(String region, String endpointOverride) {
        return new S3ClientRequest(region, endpointOverride, null);
    }

    public static S3ClientRequest from(String region, String endpointOverride, BucketCredentials bucketCredentials) {
        return new S3ClientRequest(region, endpointOverride, bucketCredentials);
    }
}
