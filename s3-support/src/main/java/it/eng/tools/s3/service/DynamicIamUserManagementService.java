package it.eng.tools.s3.service;

import io.minio.admin.MinioAdminClient;
import it.eng.tools.s3.configuration.MinioAdminClientFactory;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Data-plane {@link IamUserManagementService} dispatcher.
 *
 * <p>Active when no static {@link MinioAdminClient} bean exists (i.e. on the data plane or
 * on the control plane in AWS mode). Routes each IAM operation at call time based on the
 * {@code endpointOverride} carried by {@code managementCredentials}:</p>
 * <ul>
 *   <li>Blank or AWS endpoint ÔåÆ {@link AwsUserManagementService}</li>
 *   <li>Any other endpoint (Minio / custom S3) ÔåÆ {@link MinioUserManagementService} backed
 *       by a {@link MinioAdminClient} created on demand via {@link MinioAdminClientFactory}</li>
 * </ul>
 *
 * <p>On the control plane with a Minio endpoint, {@link MinioUserManagementService} is
 * registered directly as a Spring bean (its {@code @ConditionalOnBean(MinioAdminClient.class)}
 * fires), so this dispatcher is not active there.</p>
 */
@Component
@ConditionalOnMissingBean(MinioAdminClient.class)
@Slf4j
public class DynamicIamUserManagementService implements IamUserManagementService {

    private final MinioAdminClientFactory minioAdminClientFactory;

    /**
     * Constructs the dispatcher with the Minio admin client factory.
     *
     * @param minioAdminClientFactory factory for on-demand {@link MinioAdminClient} instances
     */
    public DynamicIamUserManagementService(MinioAdminClientFactory minioAdminClientFactory) {
        this.minioAdminClientFactory = minioAdminClientFactory;
        log.info("DynamicIamUserManagementService initialized IAM routing per request endpoint");
    }

    /**
     * Creates a user using the supplied management credentials to build the admin client.
     * This overload is the primary entry point on the data plane, where CP provides per-request
     * admin credentials. The 1-param default falls through here with null managementCredentials,
     * in which case routing falls back to {@code bucketCredentials.endpointOverride}.
     */
    @Override
    public void createUser(BucketCredentialsEntity bucketCredentials, BucketCredentialsEntity managementCredentials) {
        resolve(managementCredentials != null ? managementCredentials : bucketCredentials)
                .createUser(bucketCredentials);
    }

    @Override
    public void createUser(BucketCredentialsEntity bucketCredentials) {
        createUser(bucketCredentials, null);
    }

    @Override
    public void attachPolicyToUser(BucketCredentialsEntity bucketCredentials) {
        resolve(bucketCredentials).attachPolicyToUser(bucketCredentials);
    }

    @Override
    public void attachTemporaryPolicy(BucketCredentialsEntity managementCredentials, String accessKey,
                                      String policyName, String policyJson) {
        resolve(managementCredentials).attachTemporaryPolicy(managementCredentials, accessKey, policyName, policyJson);
    }

    @Override
    public void deleteUser(BucketCredentialsEntity managementCredentials, String accessKey) {
        resolve(managementCredentials).deleteUser(managementCredentials, accessKey);
    }

    @Override
    public void deletePolicy(BucketCredentialsEntity managementCredentials, String policyName) {
        resolve(managementCredentials).deletePolicy(managementCredentials, policyName);
    }

    /**
     * Selects the correct {@link IamUserManagementService} implementation based on the
     * {@code endpointOverride} in the supplied credentials. Uses the credentials' own
     * access key and secret key to authenticate the admin client (the caller must ensure
     * these are admin-level credentials, not end-user credentials).
     *
     * @param credentials credentials whose {@code endpointOverride}, {@code accessKey}, and
     *                    {@code secretKey} are used to determine and authenticate the backend
     * @return a {@link MinioUserManagementService} or {@link AwsUserManagementService}
     */
    private IamUserManagementService resolve(BucketCredentialsEntity credentials) {
        String endpoint = credentials.getEndpointOverride();
        if (isAwsEndpoint(endpoint)) {
            log.debug("IAM routing ÔåÆ AWS path (endpoint='{}')", endpoint);
            return new AwsUserManagementService();
        }
        log.debug("IAM routing ÔåÆ Minio path (endpoint='{}')", endpoint);
        MinioAdminClient adminClient = minioAdminClientFactory.get(
                endpoint,
                credentials.getAccessKey(),
                credentials.getSecretKey());
        return new MinioUserManagementService(adminClient);
    }

    private boolean isAwsEndpoint(String endpoint) {
        if (StringUtils.isBlank(endpoint)) {
            return true;
        }
        String lower = endpoint.toLowerCase();
        return lower.contains(".amazonaws.com") || lower.contains(".aws.");
    }
}