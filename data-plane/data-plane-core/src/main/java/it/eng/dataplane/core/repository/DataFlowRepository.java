package it.eng.dataplane.core.repository;

import it.eng.dataplane.core.model.DataFlowEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

/** Spring Data MongoDB repository for data flow persistence. */
public interface DataFlowRepository extends MongoRepository<DataFlowEntity, String> {

    /**
     * Finds a data flow by its Control Plane transfer process ID.
     *
     * @param processId the transfer process ID
     * @return optional entity
     */
    Optional<DataFlowEntity> findByProcessId(String processId);
}
