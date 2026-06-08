package it.eng.dataplane.core.repository;

import it.eng.dataplane.core.model.DataFlowCheckpoint;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Spring Data MongoDB repository for data flow checkpoint persistence. */
public interface DataFlowCheckpointRepository extends MongoRepository<DataFlowCheckpoint, String> {
}
