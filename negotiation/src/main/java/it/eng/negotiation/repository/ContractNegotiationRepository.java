package it.eng.negotiation.repository;

import it.eng.negotiation.model.ContractNegotiation;
import it.eng.tools.repository.GenericDynamicFilterRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractNegotiationRepository extends MongoRepository<ContractNegotiation, String>,
        GenericDynamicFilterRepository<ContractNegotiation, String> {

    Optional<ContractNegotiation> findByProviderPid(String providerPid);

    Optional<ContractNegotiation> findByConsumerPid(String consumerPid);

    Optional<ContractNegotiation> findByProviderPidAndConsumerPid(String providerPid, String consumerPid);

    Optional<ContractNegotiation> findByAgreement(String agreement);

    Collection<ContractNegotiation> findByStateAndRole(String state, String role);

    Collection<ContractNegotiation> findByRole(String role);

    List<ContractNegotiation> findAllByTenantId(String tenantId);

    Optional<ContractNegotiation> findByIdAndTenantId(String id, String tenantId);

    Optional<ContractNegotiation> findByProviderPidAndTenantId(String providerPid, String tenantId);

    Optional<ContractNegotiation> findByConsumerPidAndTenantId(String consumerPid, String tenantId);

    Optional<ContractNegotiation> findByProviderPidAndConsumerPidAndTenantId(String providerPid, String consumerPid, String tenantId);

    Collection<ContractNegotiation> findByStateAndRoleAndTenantId(String state, String role, String tenantId);

    Collection<ContractNegotiation> findByRoleAndTenantId(String role, String tenantId);

}
