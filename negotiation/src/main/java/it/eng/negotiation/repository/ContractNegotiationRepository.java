package it.eng.negotiation.repository;

import it.eng.negotiation.model.ContractNegotiation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractNegotiationRepository extends MongoRepository<ContractNegotiation, String> {

	Optional<ContractNegotiation> findByProviderPid(String providerPid);

	Optional<ContractNegotiation> findByProviderPidAndConsumerPid(String providerPid, String consumerPid);

	Optional<ContractNegotiation> findByConsumerPid(String consumerPid);
}
