package it.eng.negotiation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.eng.negotiation.entity.ContractNegotiationEntity;

@Repository
public interface ContractNegotiationRepository extends JpaRepository<ContractNegotiationEntity, String> {

	Optional<ContractNegotiationEntity> findByProviderPid(String providerPid);
	
	Optional<ContractNegotiationEntity> findByProviderPidAndConsumerPid(String providerPid, String consumerPid);
}
