package it.eng.dataplane.core.repository;

import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.model.DataFlowEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Spring Data MongoDB repository for data flow persistence. */
public interface DataFlowRepository extends MongoRepository<DataFlowEntity, String> {

    /**
     * Finds a data flow by its Control Plane transfer process ID.
     *
     * @param processId the transfer process ID
     * @return optional entity
     */
    Optional<DataFlowEntity> findByProcessId(String processId);

    /**
     * Finds all data flows whose state is in the given set.
     *
     * @param states the set of states to match
     * @return list of matching entities (may be empty)
     */
    List<DataFlowEntity> findAllByStateIn(Set<DataFlowState> states);
}
