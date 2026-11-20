package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.tools.service.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Keeps request bodies of the catalog API inside the current tenant.
 *
 * <p>A request body may carry an {@code @id} and references ({@code @DBRef}) to other catalog documents. Saving
 * is an upsert and references are resolved on read, so without these checks a tenant could overwrite another
 * tenant's document by reusing its id, or read it by linking it into one of its own documents.
 *
 * <p>The checks apply only when a tenant is set. A referenced id that does not exist at all is allowed, as
 * before; an id owned by another tenant, or by no tenant, is rejected with {@link CatalogErrorAPIException}
 * (400) and a message that does not say whom the id belongs to.
 */
@Component
@Slf4j
public class CatalogTenantGuard {

    private final CatalogRepository catalogRepository;
    private final DatasetRepository datasetRepository;
    private final DataServiceRepository dataServiceRepository;
    private final DistributionRepository distributionRepository;

    public CatalogTenantGuard(CatalogRepository catalogRepository, DatasetRepository datasetRepository,
                              DataServiceRepository dataServiceRepository, DistributionRepository distributionRepository) {
        this.catalogRepository = catalogRepository;
        this.datasetRepository = datasetRepository;
        this.dataServiceRepository = dataServiceRepository;
        this.distributionRepository = distributionRepository;
    }

    /**
     * Checks a catalog that is about to be created: its id and every referenced dataset, data service and
     * distribution must not belong to another tenant.
     *
     * @param catalog the catalog from the request body
     * @throws CatalogErrorAPIException if an id belongs to another tenant
     */
    public void checkNewCatalog(Catalog catalog) {
        checkId(catalog.getId(), catalogRepository.findById(catalog.getId()), Catalog::getTenantId, Catalog.class);
        checkCatalogReferences(catalog);
    }

    /**
     * Checks the references of a catalog update.
     *
     * @param catalog the catalog from the request body
     * @throws CatalogErrorAPIException if a referenced document belongs to another tenant
     */
    public void checkCatalogReferences(Catalog catalog) {
        checkDatasets(catalog.getDataset());
        checkDataServices(catalog.getService());
        checkDistributions(catalog.getDistribution());
    }

    /**
     * Checks a dataset that is about to be created: its id and its distributions must not belong to another tenant.
     *
     * @param dataset the dataset from the request body
     * @throws CatalogErrorAPIException if an id belongs to another tenant
     */
    public void checkNewDataset(Dataset dataset) {
        checkId(dataset.getId(), datasetRepository.findById(dataset.getId()), Dataset::getTenantId, Dataset.class);
        checkDatasetReferences(dataset);
    }

    /**
     * Checks the references of a dataset update.
     *
     * @param dataset the dataset from the request body; may be {@code null} when only the artifact changes
     * @throws CatalogErrorAPIException if a referenced distribution belongs to another tenant
     */
    public void checkDatasetReferences(Dataset dataset) {
        if (dataset != null) {
            checkDistributions(dataset.getDistribution());
        }
    }

    /**
     * Checks a data service that is about to be created: its id must not belong to another tenant.
     *
     * @param dataService the data service from the request body
     * @throws CatalogErrorAPIException if the id belongs to another tenant
     */
    public void checkNewDataService(DataService dataService) {
        checkId(dataService.getId(), dataServiceRepository.findById(dataService.getId()),
                DataService::getTenantId, DataService.class);
    }

    /**
     * Checks a distribution that is about to be created: its id and its access service must not belong to
     * another tenant.
     *
     * @param distribution the distribution from the request body
     * @throws CatalogErrorAPIException if an id belongs to another tenant
     */
    public void checkNewDistribution(Distribution distribution) {
        checkId(distribution.getId(), distributionRepository.findById(distribution.getId()),
                Distribution::getTenantId, Distribution.class);
        checkDistributionReferences(distribution);
    }

    /**
     * Checks the references of a distribution update.
     *
     * @param distribution the distribution from the request body
     * @throws CatalogErrorAPIException if the access service belongs to another tenant
     */
    public void checkDistributionReferences(Distribution distribution) {
        DataService accessService = distribution.getAccessService();
        if (accessService != null) {
            checkDataServices(List.of(accessService));
        }
    }

    private void checkDatasets(Collection<Dataset> datasets) {
        if (datasets != null) {
            datasets.stream().filter(Objects::nonNull).forEach(ds ->
                    checkId(ds.getId(), datasetRepository.findById(ds.getId()), Dataset::getTenantId, Dataset.class));
        }
    }

    private void checkDataServices(Collection<DataService> dataServices) {
        if (dataServices != null) {
            dataServices.stream().filter(Objects::nonNull).forEach(service ->
                    checkId(service.getId(), dataServiceRepository.findById(service.getId()),
                            DataService::getTenantId, DataService.class));
        }
    }

    private void checkDistributions(Collection<Distribution> distributions) {
        if (distributions != null) {
            distributions.stream().filter(Objects::nonNull).forEach(distribution ->
                    checkId(distribution.getId(), distributionRepository.findById(distribution.getId()),
                            Distribution::getTenantId, Distribution.class));
        }
    }

    private <T> void checkId(String id, Optional<T> stored, Function<T, String> tenantOf, Class<T> type) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || id == null) {
            return;
        }
        if (stored.isPresent() && !Objects.equals(tenantId, tenantOf.apply(stored.get()))) {
            log.warn("{} id {} in a request of tenant {} belongs to another tenant",
                    type.getSimpleName(), id, tenantId);
            throw new CatalogErrorAPIException(type.getSimpleName() + " with id " + id + " cannot be used");
        }
    }
}
