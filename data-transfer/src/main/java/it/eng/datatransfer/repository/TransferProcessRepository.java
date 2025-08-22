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
     * @param isTransferred the download status to filter by
     * @return a list of transfer processes with the specified download status
     */
    List<TransferProcess> findByIsTransferred(boolean isTransferred);

}
