package it.eng.tools.s3.provision.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class S3BucketDefinition {

    private String id;
    private String transferProcessId;
    private String regionId;
    private String bucketName;
    private String endpointOverride;

    public static class Builder {

        private final S3BucketDefinition s3BucketDefinition;

        public Builder() {
            s3BucketDefinition = new S3BucketDefinition();
        }

        @JsonCreator
        public static S3BucketDefinition.Builder newInstance() {
            return new S3BucketDefinition.Builder();
        }

        public Builder id(String id) {
            s3BucketDefinition.id = id;
            return this;
        }

        public Builder transferProcessId(String transferProcessId) {
            s3BucketDefinition.transferProcessId = transferProcessId;
            return this;
        }

        public Builder regionId(String regionId) {
            s3BucketDefinition.regionId = regionId;
            return this;
        }

        public Builder bucketName(String bucketName) {
            s3BucketDefinition.bucketName = bucketName;
            return this;
        }

        public Builder endpointOverride(String endpointOverride) {
            s3BucketDefinition.endpointOverride = endpointOverride;
            return this;
        }

        public S3BucketDefinition build() {
            return s3BucketDefinition;
        }
    }
}
