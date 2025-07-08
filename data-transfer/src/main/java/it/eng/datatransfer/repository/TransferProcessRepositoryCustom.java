package it.eng.datatransfer.repository;

import java.util.Collection;
import java.util.Map;
import it.eng.datatransfer.model.TransferProcess;

/**
 * Custom repository interface for dynamic filtering operations.
 * This interface defines data access methods that require dynamic query building.
 */
public interface TransferProcessRepositoryCustom {
    
    /**
     * Generic dynamic filtering using MongoTemplate.
     * Builds MongoDB query dynamically based on provided filter map.
     * Supports any field with automatic type detection and conversion.
     * 
     * @param filters Map of field names to filter values. All values are pre-validated and converted.
     * @return Collection of TransferProcess matching the provided criteria
     */
    Collection<TransferProcess> findWithDynamicFilters(Map<String, Object> filters);
} 