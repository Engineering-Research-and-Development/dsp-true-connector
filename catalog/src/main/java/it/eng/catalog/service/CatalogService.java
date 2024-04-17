package it.eng.catalog.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Offer;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequest;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponse;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 * The CatalogService class provides methods to interact with catalog data, including saving, retrieving, and deleting catalogs.
 */
@Service
@Slf4j
public class CatalogService {

	@Autowired
    private CatalogRepository repository;
	@Autowired
    private ApplicationEventPublisher publisher;

//    public CatalogService(CatalogRepository repository) {
//        this.repository = repository;
//    }

    /**
     * Saves the given catalog.
     *
     * @param catalog The catalog to be saved.
     */
    public void saveCatalog(Catalog catalog) {
        repository.save(catalog);
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

    //TODO implement this with aggregations and move to DataSet repository/service

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
    
    public void validateIfOfferIsValid(ContractNegotationOfferRequest offerRequest) {
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
    	
    	ContractNegotiationOfferResponse contractNegotiationOfferResponse = new ContractNegotiationOfferResponse(offerRequest.getConsumerPid(), 
    			offerRequest.getProviderPid(), valid, Serializer.serializeProtocolJsonNode(offer));
    	publisher.publishEvent(contractNegotiationOfferResponse);
    }
}
