package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.*;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * The CatalogService class provides methods to interact with catalog data, including saving, retrieving, and deleting catalogs.
 */
@Service
@Slf4j
public class CatalogService {

    private final CatalogRepository repository;
    private final AuditEventPublisher publisher;
    private final S3ClientService s3ClientService;
    private final S3Properties s3Properties;

    public CatalogService(CatalogRepository repository, AuditEventPublisher publisher, S3ClientService s3ClientService,
                          S3Properties s3Properties) {
        this.repository = repository;
        this.publisher = publisher;
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
    }

    /********* PROTOCOL ***********/
    /**
     * Retrieves the catalog.
     *
     * @return The retrieved catalog.
     * @throws CatalogErrorException Thrown if the catalog is not found.
     */
    public Catalog getCatalog() {
// TODO: remove the filtering of datasets by files in S3, after the file upload and dataset insert are separated
//  (choose artifact from files list instead of uploading when making a new dataset)
        List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());

        List<Catalog> allCatalogs = repository.findAll();

        // remove datasets that do not have files in S3
        allCatalogs.forEach(catalog -> catalog.getDataset().removeIf(
                dataset -> !files.contains(dataset.getId())));

        validateCatalog(allCatalogs);

        return allCatalogs.get(0);
    }

    /* ******** API ***********/
    /**
     * Public method for fetching the catalog for further API processing purposes.<br>
     * It throws ResourceNotFoundAPIException instead of CatalogErrorException used in protocol requests
     *
     * @return Catalog
     * @throws ResourceNotFoundAPIException Thrown if the catalog is not found.
     */
    public Catalog getCatalogForApi() {
        List<Catalog> allCatalogs = repository.findAll();

        if (allCatalogs.isEmpty()) {
            throw new ResourceNotFoundAPIException("Catalog not found");
        } else {
            return allCatalogs.get(0);
        }
    }

    private Catalog getCatalogByIdForApi(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundAPIException("Catalog with id: " + id + " not found"));
    }

    /**
     * Saves the given catalog.
     *
     * @param catalog The catalog to be saved.
     * @return Saved Catalog object
     */
    public Catalog saveCatalog(Catalog catalog) {
        // TODO handle the situation when we insert catalog for the first time, and the object like dataSets, distributions, etc. should be stored into separate documents
        Catalog storedCatalog = null;
        try {
            storedCatalog = repository.save(catalog);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerErrorAPIException("Catalog could not be saved");
        }
        return storedCatalog;
    }

    /**
     * Retrieves the catalog by its ID.
     *
     * @param id The ID of the catalog to retrieve.
     * @return An optional containing the retrieved catalog, or empty if not found.
     */
    public Catalog getCatalogById(String id) {
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
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerErrorAPIException("Catalog could not be deleted");
        }
    }

    public Catalog updateCatalog(String id, Catalog cat) {
        Catalog existingCatalog = getCatalogByIdForApi(id);
        try {
            Catalog updatedCatalog = existingCatalog.updateInstance(cat);
            return repository.save(updatedCatalog);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerErrorAPIException("Catalog could not be updated");
        }
    }

    @Deprecated
    public void validateOffer(ContractNegotationOfferRequestEvent offerRequest) {
        log.info("Comparing if offer is valid or not");
        Offer offer = CatalogSerializer.deserializeProtocol(offerRequest.getOffer(), Offer.class);
        boolean valid = validateOffer(offer);
        ContractNegotiationOfferResponseEvent contractNegotiationOfferResponse = new ContractNegotiationOfferResponseEvent(offerRequest.getConsumerPid(),
                offerRequest.getProviderPid(), valid, CatalogSerializer.serializeProtocolJsonNode(offer));
        publisher.publishEvent(contractNegotiationOfferResponse);
    }

    /**
     * Updates the catalog with a newly saved dataset reference.
     * This method adds the new dataset reference to the catalog's dataset list and saves the updated catalog.
     *
     * @param newDataset The new dataset reference to be added to the catalog.
     */
    public void updateCatalogDatasetAfterSave(Dataset newDataset) {
        // TODO handle the situation when new dataset have distribution which is not present in catalog
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
     * @param offer
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

            if (existingOffer == null) {
                log.warn("Offer with id {} not found in catalog", offer.getId());
            } else {
                // check if offers are equals
                if (offer.equals(existingOffer)) {
                    log.debug("Existing and provided offers are same");
                    valid = true;
                }
            }
        }
        log.info("Offer evaluated as {}", valid ? "valid" : "invalid");
        return valid;
    }

    private void validateCatalog(List<Catalog> allCatalogs) {
        if (allCatalogs == null
                || allCatalogs.isEmpty()
                || allCatalogs.get(0) == null) {
            log.error("Catalog is empty or not complete");
            throw new CatalogErrorException("Catalog is empty or not complete");
        }

        Catalog catalog = allCatalogs.get(0);

        if (catalog.getService() == null
                || catalog.getService().isEmpty()
                || catalog.getService().stream().anyMatch(
                        dataService -> dataService == null)) {
            log.error("Catalog does not contain any data services");
            throw new CatalogErrorException("Catalog does not contain any data services");
        }

        validateDatasets(catalog.getDataset());

        validateDistributions(catalog.getDistribution());
    }

    private static void validateDistributions(Set<Distribution> distributions) {
        if (distributions == null
                || distributions.isEmpty()
                || distributions.stream().anyMatch(
                        distribution -> distribution == null
                                || distribution.getAccessService() == null
                                || distribution.getAccessService().isEmpty())) {
            log.error("Catalog does not contain any distributions");
            throw new CatalogErrorException("Catalog does not contain any distributions");
        }
    }

    private static void validateDatasets(Set<Dataset> datasets) {
        if(datasets == null
                || datasets.isEmpty()
                || datasets.stream().anyMatch(
                        dataset -> dataset == null
                                || dataset.getHasPolicy() == null
                                || dataset.getHasPolicy().isEmpty()
                                || dataset.getDistribution() == null
                                || dataset.getDistribution().isEmpty())) {
            log.error("Catalog does not contain any datasets");
            throw new CatalogErrorException("Catalog does not contain any datasets");
        }
    }
}
