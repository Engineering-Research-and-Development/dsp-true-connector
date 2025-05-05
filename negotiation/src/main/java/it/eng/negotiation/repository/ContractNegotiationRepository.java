package it.eng.negotiation.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.negotiation.model.ContractNegotiation;

@Repository
public interface ContractNegotiationRepository extends MongoRepository<ContractNegotiation, String> {

	Optional<ContractNegotiation> findByProviderPid(String providerPid);
	
	Optional<ContractNegotiation> findByConsumerPid(String consumerPid);

	Optional<ContractNegotiation> findByProviderPidAndConsumerPid(String providerPid, String consumerPid);

	Optional<ContractNegotiation> findByAgreement(String agreement);

	Collection<ContractNegotiation> findByStateAndRole(String state, String role);
	
	Collection<ContractNegotiation> findByRole(String role);

}
