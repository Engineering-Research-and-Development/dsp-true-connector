package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.CatalogNotFoundAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Offer;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.serializer.Serializer;
import it.eng.tools.event.contractnegotiation.OfferValidationRequest;
import it.eng.tools.event.contractnegotiation.OfferValidationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The CatalogService class provides methods to interact with catalog data, including saving, retrieving, and deleting catalogs.
 */
@Service
@Slf4j
public class CatalogService {

    private final CatalogRepository repository;
    private final ApplicationEventPublisher publisher;

    public CatalogService(CatalogRepository repository, ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /**
     * Saves the given catalog.
     *
     * @param catalog The catalog to be saved.
     */
    public Catalog saveCatalog(Catalog catalog) {
        // TODO handle the situation when we insert catalog for the first time, and the object like dataSets, distributions, etc. should be stored into separate documents
        Catalog storedCatalog = repository.save(catalog);
        return storedCatalog;
    }

    /**
     * Retrieves the catalog.
     *
     * @return The retrieved catalog.
     * @throws CatalogErrorException Thrown if the catalog is not found.
     */
    public Catalog getCatalog() {
        List<Catalog> allCatalogs = repository.findAll();

        if (allCatalogs.isEmpty()) {
            throw new CatalogErrorException("Catalog not found");
        } else {
            return allCatalogs.get(0);
        }
    }

    /**
     * Retrieves the catalog by its ID.
     *
     * @param id The ID of the catalog to retrieve.
     * @return An optional containing the retrieved catalog, or empty if not found.
     */
    public Optional<Catalog> getCatalogById(String id) {
        return repository.findById(id);
    }

    //NOTE: Still present in service, because different types of errors are thrown for protocol and API
    // TODO: Find an elegant way to adapt same method in DataSetService, to throw different types of error based on Protocol/API flow

    /**
     * Retrieves a dataset by its ID.
     *
     * @param id The ID of the dataset to retrieve.
     * @return An optional containing the retrieved dataset, or empty if not found.
     */
    public Dataset getDataSetById(String id) {
        Optional<Catalog> catalog = repository.findCatalogByDatasetId(id);
        return catalog.flatMap(c -> c.getDataset()
                        .stream()
                        .filter(d -> d.getId().equals(id))
                        .findFirst())
                .orElseThrow(() -> new CatalogErrorException("Data Set with id: " + id + " not found"));
    }


    /**
     * Deletes the catalog with the specified ID.
     *
     * @param id The ID of the catalog to delete.
     */
    public void deleteCatalog(String id) {
        repository.deleteById(id);
    }

    public void validateOffer(OfferValidationRequest offerRequest) {
        log.info("Comparing if offer is valid or not");
        Offer offer = Serializer.deserializeProtocol(offerRequest.getOffer(), Offer.class);
        boolean valid = false;
//    	try {
//    		Catalog catalog = getCatalog();
//    		catalog.getDataset().forEach(ds -> {
//    			ds.getHasPolicy().forEach(p -> {
//    				if(!p.getTarget().equals(offer.getTarget())) {
//    					throw new ValidationException("Target not equal");
//    				}
//    			p.getPermission().forEach(perm -> {
//    				perm.getConstraint().forEach(constr ->{
//    					constr.getLeftOperand().equals(catalog)
//    				});
//    			});
//    			});
//    		});
        // if reached here, all checks are OK, meaning offer is valid
        log.info("Offer is valid, all checks passed ");
        valid = true;
//    	} catch (Exception ex) {
//    		log.info("Offer is NOT valid", ex.getLocalizedMessage());
//    		valid = false;
//    	}

        OfferValidationResponse offerValidationResponse = new OfferValidationResponse(offerRequest.getConsumerPid(),
                offerRequest.getProviderPid(), valid, Serializer.serializeProtocolJsonNode(offer));
        publisher.publishEvent(offerValidationResponse);
    }


    /**
     * Updates the catalog with the specified ID using the updated data.
     *
     * @param id                 The ID of the catalog to update.
     * @param updatedCatalogData The updated catalog data.
     */
    public Catalog updateCatalog(String id, Catalog updatedCatalogData) {

        Catalog.Builder builder = returnBaseCatalogForUpdate(id);
        builder
                .keyword(updatedCatalogData.getKeyword())
                .theme(updatedCatalogData.getTheme())
                .conformsTo(updatedCatalogData.getConformsTo())
                .description(updatedCatalogData.getDescription())
                .identifier(updatedCatalogData.getIdentifier())
                .title(updatedCatalogData.getTitle())
                .distribution(updatedCatalogData.getDistribution())
                .hasPolicy(updatedCatalogData.getHasPolicy())
                .dataset(updatedCatalogData.getDataset())
                .service(updatedCatalogData.getService())
                .participantId(updatedCatalogData.getParticipantId())
                .homepage(updatedCatalogData.getHomepage());

        Catalog updatedCatalog = builder.build();
        Catalog storedCatalog = repository.save(updatedCatalog);
        return storedCatalog;
    }


    /**
     * Updates the catalog with a newly saved dataset reference.
     * This method adds the new dataset reference to the catalog's dataset list and saves the updated catalog.
     *
     * @param newDataset The new dataset reference to be added to the catalog.
     */
    public void updateCatalogDatasetAfterSave(Dataset newDataset) {
        // TODO handle the situation when new dataset have distribution which is not present in catalog
        Catalog c = getCatalog();
        c.getDataset().add(newDataset);
        repository.save(c);
    }

    /**
     * Updates the catalog with modified dataset information.
     * This method replaces an existing dataset in the catalog with its updated version and saves the updated catalog.
     *
     * @param updatedDataset The dataset with updated information to be integrated into the catalog.
     */
    public void updateCatalogDatasetAfterUpdate(Dataset updatedDataset) {
        // TODO handle the situation when updated dataset have distribution which is not present in catalog
        Catalog c = getCatalog();
        c.getDataset().add(updatedDataset);
        repository.save(c);
    }

    /**
     * Removes a dataset reference from the catalog and saves the updated catalog.
     * This method removes the specified dataset reference from the catalog's dataset collection and saves the updated catalog.
     *
     * @param dataset The dataset to be removed from the catalog.
     */
    public void updateCatalogDatasetAfterDelete(Dataset dataset) {
        Catalog c = getCatalog();
        c.getDataset().remove(dataset);
        repository.save(c);
    }

    /**
     * Updates the catalog with a newly saved dataService reference.
     * This method adds the new dataService reference to the catalog's dataService list and saves the updated catalog.
     *
     * @param dataService The new data service reference to be added to the catalog.
     * @param dataService
     */
    public void updateCatalogDataServiceAfterSave(DataService dataService) {
        Catalog c = getCatalog();
        c.getService().add(dataService);
        repository.save(c);
    }


    /**
     * Updates the catalog with modified dataService information.
     * This method replaces an existing dataService in the catalog with its updated version and saves the updated catalog.
     *
     * @param dataService The dataService with updated information to be integrated into the catalog.
     */
    public void updateCatalogDataServiceAfterUpdate(DataService dataService) {
        Catalog c = getCatalog();
        c.getService().add(dataService);
        repository.save(c);
    }

    /**
     * Removes a dataService reference from the catalog and saves the updated catalog.
     * This method removes the specified dataService reference from the catalog's dataService collection and saves the updated catalog.
     *
     * @param dataService The dataService to be removed from the catalog.
     */

    public void updateCatalogDataServiceAfterDelete(DataService dataService) {
        Catalog c = getCatalog();
        c.getService().remove(dataService);
        repository.save(c);
    }

    /**
     * Private method for creating base builder for catalog update by its ID.
     *
     * @param id The ID of the catalog for update.
     * @return The builder for the catalog with basic mandatory unchanged fields.
     * @throws CatalogErrorException Thrown if the catalog with the specified ID is not found.
     */
    private Catalog.Builder returnBaseCatalogForUpdate(String id) {
        return repository.findById(id)
                .map(c -> Catalog.Builder.newInstance()
                        .id(c.getId())
                        .version(c.getVersion())
                        .issued(c.getIssued())
                        .createdBy(c.getCreatedBy()))
                .orElseThrow(() -> new CatalogNotFoundAPIException("Catalog with id: " + id + " not found"));
    }

}

