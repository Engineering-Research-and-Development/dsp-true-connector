package it.eng.tools.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface GenericDynamicFilterRepository<T, ID> {
    /**
     * Generic dynamic filtering using MongoTemplate.
     * Builds MongoDB query dynamically based on provided filter map.
     *
     * @param filters     Map of field names to filter values
     * @param entityClass Class of the entity to filter
     * @param pageable    Pagination information
     * @return Collection of entities matching the provided criteria
     */
    Page<T> findWithDynamicFilters(Map<String, Object> filters, Class<T> entityClass, Pageable pageable);

}
