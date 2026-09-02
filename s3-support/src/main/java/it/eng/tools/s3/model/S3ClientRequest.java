package it.eng.tools.s3.model;

public record S3ClientRequest(String region, String endpointOverride, BucketCredentialsEntity bucketCredentials) {

    public static S3ClientRequest from(String region, String endpointOverride) {
        return new S3ClientRequest(region, endpointOverride, null);
    }

    public static S3ClientRequest from(String region, String endpointOverride, BucketCredentialsEntity bucketCredentials) {
        return new S3ClientRequest(region, endpointOverride, bucketCredentials);
    }
}
