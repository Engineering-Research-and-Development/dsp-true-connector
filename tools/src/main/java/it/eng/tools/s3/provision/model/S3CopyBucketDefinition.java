package it.eng.tools.s3.provision.model;

import it.eng.tools.s3.provision.SecretToken;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class S3CopyBucketDefinition {

    private String sourceBucket;
    private String sourceKey;

    private String destinationBucket;
    private String destinationKey;
    private String destinationRegion;

    private String endpointOverride;
    private S3ProvisionResponse bucketProvisionResponse;
    private SecretToken secretToken;

    public static class Builder {
        private final S3CopyBucketDefinition definition;

        public Builder() {
            this.definition = new S3CopyBucketDefinition();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder sourceBucket(String sourceBucket) {
            definition.sourceBucket = sourceBucket;
            return this;
        }

        public Builder sourceKey(String sourceKey) {
            definition.sourceKey = sourceKey;
            return this;
        }

        public Builder destinationBucket(String destinationBucket) {
            definition.destinationBucket = destinationBucket;
            return this;
        }

        public Builder destinationKey(String destinationKey) {
            definition.destinationKey = destinationKey;
            return this;
        }

        public Builder destinationRegion(String destinationRegion) {
            definition.destinationRegion = destinationRegion;
            return this;
        }

        public Builder endpointOverride(String endpointOverride) {
            definition.endpointOverride = endpointOverride;
            return this;
        }

        public Builder secretToken(SecretToken secretToken) {
            definition.secretToken = secretToken;
            return this;
        }

        public Builder bucketProvisionResponse(S3ProvisionResponse bucketProvisionResponse) {
            definition.bucketProvisionResponse = bucketProvisionResponse;
            return this;
        }

        public S3CopyBucketDefinition build() {
            return definition;
        }
    }
}
