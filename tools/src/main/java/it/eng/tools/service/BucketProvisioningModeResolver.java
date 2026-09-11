package it.eng.tools.service;

import it.eng.tools.model.BucketProvisioningMode;
import it.eng.tools.model.TenantBucketCredentialsRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Resolves the {@link BucketProvisioningMode} implied by the optional
 * {@code bucketName}/{@code accessKey}/{@code secretKey} fields of a
 * {@link TenantBucketCredentialsRequest}, applying the partial-input classification matrix
 * agreed for tenant bucket credential onboarding.
 */
@Service
public class BucketProvisioningModeResolver {

    /**
     * Classifies the given request into one of the valid {@link BucketProvisioningMode} values.
     *
     * @param request the tenant bucket credentials request to classify
     * @return {@link BucketProvisioningMode#AUTOMATIC} when no fields are supplied,
     *         {@link BucketProvisioningMode#EXISTING_BUCKET} when only {@code bucketName} is supplied,
     *         or {@link BucketProvisioningMode#EXTERNAL_CREDENTIALS} when {@code bucketName},
     *         {@code accessKey}, and {@code secretKey} are all supplied
     * @throws IllegalArgumentException if the supplied fields form any other, invalid combination
     */
    public BucketProvisioningMode resolve(TenantBucketCredentialsRequest request) {
        boolean hasBucketName = StringUtils.isNotBlank(request.getBucketName());
        boolean hasAccessKey = StringUtils.isNotBlank(request.getAccessKey());
        boolean hasSecretKey = StringUtils.isNotBlank(request.getSecretKey());

        if (!hasBucketName && !hasAccessKey && !hasSecretKey) {
            return BucketProvisioningMode.AUTOMATIC;
        }
        if (hasBucketName && !hasAccessKey && !hasSecretKey) {
            return BucketProvisioningMode.EXISTING_BUCKET;
        }
        if (hasBucketName && hasAccessKey && hasSecretKey) {
            return BucketProvisioningMode.EXTERNAL_CREDENTIALS;
        }

        throw new IllegalArgumentException("Invalid bucket credentials request combination: " +
                "bucketName=" + (hasBucketName ? "present" : "absent") +
                ", accessKey=" + (hasAccessKey ? "present" : "absent") +
                ", secretKey=" + (hasSecretKey ? "present" : "absent"));
    }
}
