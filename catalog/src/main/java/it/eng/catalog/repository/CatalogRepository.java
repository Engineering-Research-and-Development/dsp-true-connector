package it.eng.catalog.repository;

import it.eng.catalog.model.Catalog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogRepository extends MongoRepository<Catalog, String> {
    @Query(value = "{'service.id': ?0}", fields = "{'service.$': 1}")
    Optional<Catalog> findCatalogByDataServiceId(String dataServiceId);

    @Query(value = "{'dataset.id': ?0}", fields = "{'dataset.$': 1}")
    Optional<Catalog> findCatalogByDatasetId(String datasetId);
}
