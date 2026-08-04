package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.*;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantBucketResolver;
import it.eng.tools.service.TenantContextHolder;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The CatalogService class provides methods to interact with catalog data, including saving, retrieving, and deleting catalogs.
 */
@Service
@Slf4j
public class CatalogService {

    private static final String ERROR_MESSAGE_CATALOG_NOT_AVAILABLE = "Catalog not available at the moment";

    private final CatalogRepository repository;
    private final AuditEventPublisher publisher;
    private final S3ClientService s3ClientService;
    private final TenantBucketResolver tenantBucketResolver;

    public CatalogService(CatalogRepository repository, AuditEventPublisher publisher, S3ClientService s3ClientService,
                          TenantBucketResolver tenantBucketResolver) {
        this.repository = repository;
        this.publisher = publisher;
        this.s3ClientService = s3ClientService;
        this.tenantBucketResolver = tenantBucketResolver;
    }

    /********* PROTOCOL ***********/
    /**
     * Retrieves the catalog scoped to the current tenant context.
     * When a tenant ID is set via {@link TenantContextHolder}, only catalogs owned
     * by that tenant are returned. Super-admin requests (null tenant ID) see all catalogs.
     *
     * @return The retrieved catalog.
     * @throws CatalogErrorException Thrown if the catalog is not found.
     */
    public Catalog getCatalog() {
// TODO: remove the filtering of datasets by files in S3, after the file upload and dataset insert are separated
//  (choose artifact from files list instead of uploading when making a new dataset)
        List<String> files = s3ClientService.listFiles(tenantBucketResolver.resolveBucketName());
        String tenantId = TenantContextHolder.getTenantId();

        List<Catalog> allCatalogs;
        if (tenantId != null) {
            allCatalogs = repository.findAllByTenantId(tenantId);
        } else {
            allCatalogs = repository.findAll();
        }

        log.info("Catalogs found: {}", allCatalogs.size());

        // remove datasets that do not have files in S3
        // external files can not be checked at the time of writing and will be automatically allowed
        allCatalogs.forEach(catalog -> catalog.getDataset().removeIf(
                dataset -> dataset.getArtifact().getArtifactType() == ArtifactType.FILE
                        && !files.contains(dataset.getId())));

        try {
            validateCatalog(allCatalogs);
        } catch (ValidationException e) {
            log.error("Catalog failed on protocol validation: {}", e.getMessage());
            publisher.publishEvent(AuditEventType.PROTOCOL_CATALOG_CATALOG_NOT_FOUND,
                    "Catalog protocol validation failed",
                    Map.of("role", IConstants.ROLE_PROTOCOL,
                            "errorMessage", e.getMessage()));
            throw new CatalogErrorException(ERROR_MESSAGE_CATALOG_NOT_AVAILABLE);

        }
        return allCatalogs.get(0);
    }

    /* ******** API ***********/

    /**
     * Public method for fetching the catalog for further API processing purposes.
     * Scoped to the current tenant context when a tenant ID is set.
     * It throws ResourceNotFoundAPIException instead of CatalogErrorException used in protocol requests.
     *
     * @return Catalog
     * @throws ResourceNotFoundAPIException Thrown if the catalog is not found.
     */
    public Catalog getCatalogForApi() {
        String tenantId = TenantContextHolder.getTenantId();
        List<Catalog> allCatalogs;
        if (tenantId != null) {
            allCatalogs = repository.findAllByTenantId(tenantId);
        } else {
            allCatalogs = repository.findAll();
        }

        if (allCatalogs.isEmpty()) {
            throw new ResourceNotFoundAPIException("Catalog not found");
        } else {
            return allCatalogs.get(0);
        }
    }

    private Catalog getCatalogByIdForApi(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return repository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundAPIException("Catalog with id: " + id + " not found"));
        }
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundAPIException("Catalog with id: " + id + " not found"));
    }

    /**
     * Saves the given catalog, stamping it with the current tenant ID if one is set.
     *
     * @param catalog The catalog to be saved.
     * @return Saved Catalog object
     */
    public Catalog saveCatalog(Catalog catalog) {
        // TODO handle the situation when we insert catalog for the first time, and the object like dataSets, distributions, etc. should be stored into separate documents
        Catalog storedCatalog = null;
        try {
            String tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null && catalog.getTenantId() == null) {
                catalog.injectTenantId(tenantId);
            }
            storedCatalog = repository.save(catalog);
            publisher.publishEvent(AuditEventType.CATALOG_CREATED, "Catalog created", Map.of("catalogId", storedCatalog.getId()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerErrorAPIException("Catalog could not be saved");
        }
        return storedCatalog;
    }

    /**
     * Retrieves the catalog by its ID, scoped to the current tenant context.
     *
     * @param id The ID of the catalog to retrieve.
     * @return An optional containing the retrieved catalog, or empty if not found.
     */
    public Catalog getCatalogById(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return repository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundAPIException("Catalog with id" + id + " not found"));
        }
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundAPIException("Catalog with id" + id + " not found"));
    }

    /**
     * Deletes the catalog with the specified ID.
     *
     * @param id The ID of the catalog to delete.
     */
    public void deleteCatalog(String id) {
        getCatalogById(id);
        try {
            repository.deleteById(id);
            publisher.publishEvent(AuditEventType.CATALOG_DELETED, "Catalog deleted", Map.of("catalogId", id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerErrorAPIException("Catalog could not be deleted");
        }
    }

    public Catalog updateCatalog(String id, Catalog cat) {
        Catalog existingCatalog = getCatalogByIdForApi(id);
        try {
            Catalog updatedCatalog = existingCatalog.updateInstance(cat);
            Catalog saved = repository.save(updatedCatalog);
            publisher.publishEvent(AuditEventType.CATALOG_UPDATED, "Catalog updated", Map.of("catalogId", id));
            return saved;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerErrorAPIException("Catalog could not be updated");
        }
    }


    /**
     * Updates the catalog with a newly saved dataset reference.
     * This method adds the new dataset reference to the catalog's dataset list and saves the updated catalog.
     *
     * @param newDataset The new dataset reference to be added to the catalog.
     */
    public void updateCatalogDatasetAfterSave(Dataset newDataset) {
        // TODO handle the situation when new dataset have distribution which is not present in catalog
        assertSameTenant(TenantContextHolder.getTenantId(), newDataset.getTenantId(), Dataset.class.getSimpleName(), newDataset.getId());
        Catalog c = getCatalogForApi();
        c.getDataset().add(newDataset);
        saveCatalog(c);
    }

    /**
     * Removes a dataset reference from the catalog and saves the updated catalog.
     * This method removes the specified dataset reference from the catalog's dataset collection and saves the updated catalog.
     *
     * @param dataset The dataset to be removed from the catalog.
     */
    public void updateCatalogDatasetAfterDelete(Dataset dataset) {
        Catalog c = getCatalogForApi();
        c.getDataset().remove(dataset);
        saveCatalog(c);
    }

    /**
     * Updates the catalog with a newly saved dataService reference.
     * This method adds the new dataService reference to the catalog's dataService list and saves the updated catalog.
     *
     * @param dataService The new data service reference to be added to the catalog.
     */
    public void updateCatalogDataServiceAfterSave(DataService dataService) {
        assertSameTenant(TenantContextHolder.getTenantId(), dataService.getTenantId(), DataService.class.getSimpleName(), dataService.getId());
        Catalog c = getCatalogForApi();
        c.getService().add(dataService);
        saveCatalog(c);
    }


    /**
     * Removes a dataService reference from the catalog and saves the updated catalog.
     * This method removes the specified dataService reference from the catalog's dataService collection and saves the updated catalog.
     *
     * @param dataService The dataService to be removed from the catalog.
     */

    public void updateCatalogDataServiceAfterDelete(DataService dataService) {
        Catalog c = getCatalogForApi();
        c.getService().remove(dataService);
        saveCatalog(c);
    }

    /**
     * Updates the catalog with a newly saved distribution reference.
     * This method adds the new distribution reference to the catalog's distribution list and saves the updated catalog.
     *
     * @param newDistribution The new distribution reference to be added to the catalog.
     */
    public void updateCatalogDistributionAfterSave(Distribution newDistribution) {
        assertSameTenant(TenantContextHolder.getTenantId(), newDistribution.getTenantId(), Distribution.class.getSimpleName(), newDistribution.getId());
        Catalog c = getCatalogForApi();
        c.getDistribution().add(newDistribution);
        saveCatalog(c);
    }

    /**
     * Removes a distribution reference from the catalog and saves the updated catalog.
     * This method removes the specified distribution reference from the catalog's distribution collection and saves the updated catalog.
     *
     * @param distribution The distribution to be removed from the catalog.
     */

    public void updateCatalogDistributionAfterDelete(Distribution distribution) {
        Catalog c = getCatalogForApi();
        c.getDistribution().remove(distribution);
        saveCatalog(c);
    }


    /**
     * Used by the protocol business logic to check if such offer exists.
     * because of that reason it throws CatalogErrorException instead of ResourceNotFoundAPIException
     *
     * @param offer The offer to validate.
     * @return boolean
     */
    public boolean validateOffer(Offer offer) {
        boolean valid = false;
        Catalog catalog = getCatalog();
        Dataset dataset = catalog.getDataset().stream()
                .filter(ds -> ds.getId().equals(offer.getTarget())).findFirst()
                .orElse(null);
        if (dataset == null) {
            log.warn("Offer.target '{}' does not match with any dataset from catalog", offer.getTarget());
        } else {

            Offer existingOffer = dataset.getHasPolicy().stream()
                    .filter(of -> of.getId().equals(offer.getId()))
                    .findFirst()
                    .orElse(null);

            log.debug("Offer with id '{}' {}", offer.getId(), existingOffer != null ? " found." : "not found.");

            if (existingOffer != null) {
                valid = true;
            }
        }
        log.info("Offer evaluated as {}", valid ? "valid" : "invalid");
        return valid;
    }

    /**
     * Asserts that the caller tenant and the document tenant are the same, preventing cross-tenant
     * references from being persisted.
     *
     * @param callerTenantId   the tenant ID of the current caller from {@link TenantContextHolder}
     * @param documentTenantId the tenant ID stamped on the document being referenced
     * @param documentType     a human-readable type label used in the error message and warning log
     * @param documentId       the document identifier used in the error message and warning log
     * @throws InternalServerErrorAPIException if both tenant IDs are non-null and not equal
     */
    private void assertSameTenant(String callerTenantId, String documentTenantId, String documentType, String documentId) {
        if (callerTenantId != null && documentTenantId != null && !callerTenantId.equals(documentTenantId)) {
            log.warn("Cross-tenant {} reference detected: caller tenant={}, document tenant={}, id={}",
                    documentType, callerTenantId, documentTenantId, documentId);
            throw new InternalServerErrorAPIException(
                    "Cross-tenant reference rejected: " + documentType + " id=" + documentId);
        }
    }

    private void validateCatalog(List<Catalog> allCatalogs) {
        // Validate catalog collection
        if (allCatalogs == null || allCatalogs.isEmpty()) {
            throw new ValidationException("Catalog not found");
        }
        // Check if there's at least one non-null dataset
        if (allCatalogs.stream().noneMatch(Objects::nonNull)) {
            throw new ValidationException("Catalog not found");
        }

        allCatalogs.forEach(Catalog::validateProtocol);
    }
}
