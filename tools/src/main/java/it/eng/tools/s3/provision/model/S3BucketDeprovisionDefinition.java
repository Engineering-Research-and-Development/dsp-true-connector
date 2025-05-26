package it.eng.tools.s3.provision.model;

import it.eng.tools.s3.provision.SecretToken;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
/**
 * This class is used to define the deprovisioning of an S3 bucket.
 * It contains the necessary information to remove the bucket and its associated resources.
 */
public class S3BucketDeprovisionDefinition {

    private String sourceAccountRoleName;
    private String destinationRegion;
    private String destinationBucketName;
    private String destinationKeyName;
    private String bucketPolicyStatementSid;
    private String endpointOverride;

    private SecretToken secretToken;

    public static class Builder {
        private final S3BucketDeprovisionDefinition definition;

        public Builder() {
            this.definition = new S3BucketDeprovisionDefinition();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder sourceAccountRoleName(String roleName) {
            definition.sourceAccountRoleName = roleName;
            return this;
        }

        public Builder destinationRegion(String region) {
            definition.destinationRegion = region;
            return this;
        }

        public Builder destinationBucketName(String bucketName) {
            definition.destinationBucketName = bucketName;
            return this;
        }

        public Builder destinationKeyName(String destinationKeyName) {
            definition.destinationKeyName = destinationKeyName;
            return this;
        }

        public Builder bucketPolicyStatementSid(String statementSid) {
            definition.bucketPolicyStatementSid = statementSid;
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

        public S3BucketDeprovisionDefinition build() {
            return definition;
        }
    }

}
