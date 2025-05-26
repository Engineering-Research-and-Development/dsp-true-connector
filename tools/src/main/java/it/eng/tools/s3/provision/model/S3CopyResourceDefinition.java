package it.eng.tools.s3.provision.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class S3CopyResourceDefinition {

    private String transferProcessId;

    private String endpointOverride;
    private String destinationRegion;
    private String destinationBucketName;
    private String destinationObjectName;
    private String destinationKeyName;
    private String bucketPolicyStatementSid;

    public static class Builder {

        private final S3CopyResourceDefinition s3CopyResourceDefinition;

        public Builder() {
            s3CopyResourceDefinition = new S3CopyResourceDefinition();
        }

        public static S3CopyResourceDefinition.Builder newInstance() {
            return new S3CopyResourceDefinition.Builder();
        }

        public Builder transferProcessId(String transferProcessId) {
            s3CopyResourceDefinition.transferProcessId = transferProcessId;
            return this;
        }

        public Builder endpointOverride(String endpointOverride) {
            s3CopyResourceDefinition.endpointOverride = endpointOverride;
            return this;
        }

        public Builder destinationRegion(String destinationRegion) {
            s3CopyResourceDefinition.destinationRegion = destinationRegion;
            return this;
        }

        public Builder destinationBucketName(String destinationBucketName) {
            s3CopyResourceDefinition.destinationBucketName = destinationBucketName;
            return this;
        }

        public Builder destinationObjectName(String destinationObjectName) {
            s3CopyResourceDefinition.destinationObjectName = destinationObjectName;
            return this;
        }

        public Builder destinationKeyName(String destinationKeyName) {
            s3CopyResourceDefinition.destinationKeyName = destinationKeyName;
            return this;
        }

        public Builder bucketPolicyStatementSid(String bucketPolicyStatementSid) {
            s3CopyResourceDefinition.bucketPolicyStatementSid = bucketPolicyStatementSid;
            return this;
        }

        public S3CopyResourceDefinition build() {
            return s3CopyResourceDefinition;
        }
    }

}
