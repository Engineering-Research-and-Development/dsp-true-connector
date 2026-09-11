package it.eng.datatransfer.repository;

import it.eng.datatransfer.model.TransferProcess;
import it.eng.tools.repository.GenericDynamicFilterRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransferProcessRepository extends MongoRepository<TransferProcess, String>,
        GenericDynamicFilterRepository<TransferProcess, String> {

    /**
     * Finds a transfer process by its consumerPid and providerPid.
     *
     * @param consumerPid the PID of the consumer
     * @param providerPid the PID of the provider
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByConsumerPidAndProviderPid(String consumerPid, String providerPid);

    /**
     * Finds a transfer process by ProviderPid.
     *
     * @param providerPid the providerPid of the transfer process
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByProviderPid(String providerPid);


    /**
     * Finds a transfer process by ConsumerPid.
     *
     * @param consumerPid the consumerPid of the transfer process
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByConsumerPid(String consumerPid);

    /**
     * Finds a transfer process by agreementId.
     *
     * @param agreementId the agreementId of the transfer process
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByAgreementId(String agreementId);

    /**
     * Finds a transfer process by its state and role.
     *
     * @param state the state of the transfer process
     * @param role  the role of user in the transfer process
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Collection<TransferProcess> findByStateAndRole(String state, String role);

    /**
     * Finds all transfer processes by role.
     *
     * @param role the role of user in the transfer process
     * @return a list of transfer processes with the specified state
     */
    Collection<TransferProcess> findByRole(String role);

    /**
     * Finds all transfer processes by download status.
     *
     * @param isDownloaded the download status to filter by
     * @return a list of transfer processes with the specified download status
     */
    List<TransferProcess> findByIsDownloaded(boolean isDownloaded);

    /**
     * Finds a transfer process by its consumerPid, providerPid, and tenantId.
     *
     * @param consumerPid the PID of the consumer
     * @param providerPid the PID of the provider
     * @param tenantId    the tenant identifier
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByConsumerPidAndProviderPidAndTenantId(String consumerPid, String providerPid, String tenantId);

    /**
     * Finds a transfer process by ProviderPid and tenantId.
     *
     * @param providerPid the providerPid of the transfer process
     * @param tenantId    the tenant identifier
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByProviderPidAndTenantId(String providerPid, String tenantId);

    /**
     * Finds a transfer process by ConsumerPid and tenantId.
     *
     * @param consumerPid the consumerPid of the transfer process
     * @param tenantId    the tenant identifier
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByConsumerPidAndTenantId(String consumerPid, String tenantId);

    /**
     * Finds a transfer process by agreementId and tenantId.
     *
     * @param agreementId the agreementId of the transfer process
     * @param tenantId    the tenant identifier
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByAgreementIdAndTenantId(String agreementId, String tenantId);

    /**
     * Finds all transfer processes by state, role, and tenantId.
     *
     * @param state    the state of the transfer process
     * @param role     the role of user in the transfer process
     * @param tenantId the tenant identifier
     * @return a collection of matching transfer processes
     */
    Collection<TransferProcess> findByStateAndRoleAndTenantId(String state, String role, String tenantId);

    /**
     * Finds all transfer processes by role and tenantId.
     *
     * @param role     the role of user in the transfer process
     * @param tenantId the tenant identifier
     * @return a collection of matching transfer processes
     */
    Collection<TransferProcess> findByRoleAndTenantId(String role, String tenantId);

    /**
     * Finds a transfer process by its internal id and tenantId.
     *
     * @param id       the internal MongoDB id
     * @param tenantId the tenant identifier
     * @return an Optional containing the TransferProcess if found, or empty if not found
     */
    Optional<TransferProcess> findByIdAndTenantId(String id, String tenantId);

    /**
     * Finds all transfer processes where a download is currently flagged as in progress.
     * Used at startup to reset stale {@code isDownloadInProgress=true} records left by a previous crash.
     *
     * @return a list of transfer processes with {@code isDownloadInProgress} set to {@code true}
     */
    List<TransferProcess> findAllByIsDownloadInProgressTrue();

}