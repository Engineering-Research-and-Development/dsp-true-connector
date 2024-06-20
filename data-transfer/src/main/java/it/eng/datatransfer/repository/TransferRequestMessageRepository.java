package it.eng.datatransfer.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.datatransfer.model.TransferRequestMessage;

@Repository
public interface TransferRequestMessageRepository extends MongoRepository<TransferRequestMessage, String> {

    Optional<TransferRequestMessage> findByAgreementId(String agreementId);
    
    Optional<TransferRequestMessage> findByConsumerPid(String consumerPid);
}
