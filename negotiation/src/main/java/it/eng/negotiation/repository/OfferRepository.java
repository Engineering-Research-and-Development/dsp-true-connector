package it.eng.negotiation.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.negotiation.model.Offer;

@Repository
public interface OfferRepository extends MongoRepository<Offer, String>{

}
