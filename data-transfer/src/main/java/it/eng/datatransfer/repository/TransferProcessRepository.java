package it.eng.datatransfer.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.datatransfer.model.TransferProcess;

@Repository
public interface TransferProcessRepository extends MongoRepository<TransferProcess, String> {

    Optional<TransferProcess> findByConsumerPidAndProviderPid(String consumerPid, String providerPid);
    
    Optional<TransferProcess> findByProviderPid(String providerPid);
    
    Optional<TransferProcess> findByAgreementId(String agreementId);
    
	Collection<TransferProcess> findByStateAndRole(String state, String role);

	Collection<TransferProcess> findByRole(String role);

    /**
     * Finds all transfer processes by download status.
     *
     * @param isDownloaded the download status to filter by
     * @return a list of transfer processes with the specified download status
     */
    List<TransferProcess> findByIsDownloaded(boolean isDownloaded);
}
