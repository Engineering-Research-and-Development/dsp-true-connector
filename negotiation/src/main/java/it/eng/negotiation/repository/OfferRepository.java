package it.eng.negotiation.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.negotiation.model.Offer;

@Repository
public interface OfferRepository extends MongoRepository<Offer, String>{

	Optional<Offer> findByConsumerPidAndProviderPidAndTarget(String consumerPid, String providerPid, String target);

}
