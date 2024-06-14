package it.eng.negotiation.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.negotiation.model.Agreement;

@Repository
public interface AgreementRepository extends MongoRepository<Agreement, String> {

}
