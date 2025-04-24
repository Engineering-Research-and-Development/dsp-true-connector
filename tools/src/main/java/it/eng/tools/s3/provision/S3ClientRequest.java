package it.eng.tools.s3.provision;

public record S3ClientRequest(String region, String endpointOverride, SecretToken secretToken) {

    public static S3ClientRequest from(String region, String endpointOverride) {
        return new S3ClientRequest(region, endpointOverride, null);
    }

    public static S3ClientRequest from(String region, String endpointOverride, SecretToken secretToken) {
        return new S3ClientRequest(region, endpointOverride, secretToken);
    }

}
