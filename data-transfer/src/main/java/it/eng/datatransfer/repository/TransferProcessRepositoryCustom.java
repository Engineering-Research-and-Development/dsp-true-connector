package it.eng.datatransfer.repository;

import java.util.Collection;
import it.eng.datatransfer.model.TransferProcess;

/**
 * Custom repository interface for dynamic filtering operations.
 * This interface defines data access methods that require dynamic query building.
 */
public interface TransferProcessRepositoryCustom {
    
    /**
     * Dynamic filtering using MongoTemplate.
     * Builds MongoDB query dynamically based on provided parameters.
     * Only adds criteria for non-null and non-empty parameters.
     * 
     * @param state       transfer state to filter by (nullable/empty means ignore)
     * @param role        transfer role to filter by (nullable/empty means ignore)
     * @param datasetId   dataset identifier to filter by (nullable/empty means ignore)
     * @param providerPid provider PID to filter by (nullable/empty means ignore)
     * @param consumerPid consumer PID to filter by (nullable/empty means ignore)
     * @return Collection of TransferProcess matching the provided criteria
     */
    Collection<TransferProcess> findWithDynamicFilters(String state, String role, 
                                                      String datasetId, String providerPid, 
                                                      String consumerPid);
} 