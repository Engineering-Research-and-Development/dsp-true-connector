package it.eng.tools.repository;

import java.util.Collection;
import java.util.Map;

public interface GenericDynamicFilterRepository<T, ID> {
    /**
     * Generic dynamic filtering using MongoTemplate.
     * Builds MongoDB query dynamically based on provided filter map.
     *
     * @param filters     Map of field names to filter values
     * @param entityClass Class of the entity to filter
     * @return Collection of entities matching the provided criteria
     */
    Collection<T> findWithDynamicFilters(Map<String, Object> filters, Class<T> entityClass);

}
