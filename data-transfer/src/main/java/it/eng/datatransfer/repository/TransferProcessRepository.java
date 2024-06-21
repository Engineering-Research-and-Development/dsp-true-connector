package it.eng.datatransfer.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.datatransfer.model.TransferProcess;

@Repository
public interface TransferProcessRepository extends MongoRepository<TransferProcess, String> {

    Optional<TransferProcess> findByConsumerPidAndProviderPid(String consumerPid, String providerPid);
    
    Optional<TransferProcess> findByProviderPid(String providerPid);
}
