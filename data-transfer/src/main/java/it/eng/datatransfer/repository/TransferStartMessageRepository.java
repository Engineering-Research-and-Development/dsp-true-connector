package it.eng.datatransfer.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.datatransfer.model.TransferStartMessage;

@Repository
public interface TransferStartMessageRepository extends MongoRepository<TransferStartMessage, String> {

    Optional<TransferStartMessage> findByConsumerPidAndProviderPid(String consumerPid, String providerPid);
}
