package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.model.Action;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Constraint;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.LeftOperand;
import it.eng.catalog.model.Offer;
import it.eng.catalog.model.Operator;
import it.eng.catalog.model.Permission;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceTest {

    @Mock
    private CatalogRepository repository;
    @Mock
    private ApplicationEventPublisher publisher;
    
    @Captor
	private ArgumentCaptor<ContractNegotiationOfferResponseEvent> argCaptorContractNegotiationOfferResponse;
    
	@Captor
	private ArgumentCaptor<Catalog> argCaptorCatalog;

    @InjectMocks
    private CatalogService service;

    @Test
    @DisplayName("Save catalog successfully")
    void saveCatalog_success() {
        when(repository.save(any(Catalog.class))).thenReturn(MockObjectUtil.CATALOG);
        Catalog savedCatalog = service.saveCatalog(MockObjectUtil.CATALOG);
        assertNotNull(savedCatalog);
        verify(repository).save(MockObjectUtil.CATALOG);
    }

    @Test
    @DisplayName("Get catalog successfully")
    void getCatalog_success() {
        when(repository.findAll()).thenReturn(Collections.singletonList(MockObjectUtil.CATALOG));
        Catalog retrievedCatalog = service.getCatalog();
        assertNotNull(retrievedCatalog);
        verify(repository).findAll();
    }

    @Test
    @DisplayName("Get catalog throws exception when not found")
    void getCatalog_notFound() {
        when(repository.findAll()).thenReturn(Collections.emptyList());
        assertThrows(CatalogErrorException.class, () -> service.getCatalog());
    }

    @Test
    @DisplayName("Get catalog by ID successfully")
    void getCatalogById_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(MockObjectUtil.CATALOG));
        Catalog retrievedCatalog = service.getCatalogById(MockObjectUtil.CATALOG.getId());
        assertNotNull(retrievedCatalog);
        verify(repository).findById(MockObjectUtil.CATALOG.getId());
    }

    @Test
    @DisplayName("Delete catalog successfully")
    void deleteCatalog_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(MockObjectUtil.CATALOG));
        service.deleteCatalog(MockObjectUtil.CATALOG.getId());
        verify(repository).deleteById(MockObjectUtil.CATALOG.getId());
    }

    @Test
    @DisplayName("Update catalog successfully")
    void updateCatalog_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(MockObjectUtil.CATALOG));
        when(repository.save(any(Catalog.class))).thenReturn(MockObjectUtil.CATALOG);

        Catalog updatedCatalogData = MockObjectUtil.CATALOG_FOR_UPDATE;
        
        Catalog updatedCatalog = service.updateCatalog(MockObjectUtil.CATALOG.getId(), updatedCatalogData);
        assertNotNull(updatedCatalog);
        verify(repository).findById(MockObjectUtil.CATALOG.getId());
        verify(repository).save(argCaptorCatalog.capture());
        assertTrue(argCaptorCatalog.getValue().getDescription().stream().filter(d -> d.getValue().contains("update")).findFirst().isPresent());
        assertTrue(argCaptorCatalog.getValue().getDistribution().stream().filter(d -> d.getTitle().contains("update")).findFirst().isPresent());
       
        assertTrue(argCaptorCatalog.getValue().getDistribution().stream().findFirst().get().getHasPolicy()
        		.stream()
        		.filter(p -> p.getId().equals("urn:offer_id_update"))
        		.findFirst().isPresent());
        
        DataService dataServiceUpdated = argCaptorCatalog.getValue().getService().stream().findFirst().get();
        assertTrue(dataServiceUpdated.getCreator().contains("update"));
        assertTrue(dataServiceUpdated.getEndpointURL().contains("update"));
        assertTrue(dataServiceUpdated.getEndpointDescription().contains("update"));
    }

    @Test
    @DisplayName("Update catalog data service after delete successfully")
    void updateCatalogDataServiceAfterDelete_success() {
    	
        DataService dataService = MockObjectUtil.DATA_SERVICE;
        when(repository.findAll()).thenReturn(Collections.singletonList(MockObjectUtil.CATALOG));
        when(repository.save(any(Catalog.class))).thenReturn(MockObjectUtil.CATALOG);

        service.updateCatalogDataServiceAfterDelete(dataService);

        verify(repository).save(any(Catalog.class));
    }
    
    @Test
    public void providedOfferExists() {
    	when(repository.findAll()).thenReturn(new ArrayList<>(MockObjectUtil.CATALOGS));
    	ContractNegotationOfferRequestEvent offerRequest = new ContractNegotationOfferRequestEvent(MockObjectUtil.CONSUMER_PID,
    			MockObjectUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.OFFER_WITH_TARGET));
    	service.validateOffer(offerRequest);
    	
    	verify(publisher).publishEvent(argCaptorContractNegotiationOfferResponse.capture());
    	assertTrue(argCaptorContractNegotiationOfferResponse.getValue().isOfferAccepted());
    }

    @Test
    public void providedOfferNotFound() {
	  Offer differentOffer = Offer.Builder.newInstance()
	    		.id("urn:offer_id")
	            .target(MockObjectUtil.TARGET)
	            .permission(Set.of(MockObjectUtil.PERMISSION_ANONYMIZE))
	            .build();
	
    	when(repository.findAll()).thenReturn(new ArrayList<>(MockObjectUtil.CATALOGS));
    	ContractNegotationOfferRequestEvent offerRequest = new ContractNegotationOfferRequestEvent(MockObjectUtil.CONSUMER_PID,
    			MockObjectUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(differentOffer));
    	service.validateOffer(offerRequest);
    	
    	verify(publisher).publishEvent(argCaptorContractNegotiationOfferResponse.capture());
    	assertFalse(argCaptorContractNegotiationOfferResponse.getValue().isOfferAccepted());
    }
    
    @Test
    @DisplayName("Offer valid")
    public void valiadateOffer( ) {
    	when(repository.findAll()).thenReturn(new ArrayList<>(MockObjectUtil.CATALOGS));
    	
    	boolean offerValid = service.validateOffer(MockObjectUtil.OFFER_WITH_TARGET);
    
    	assertTrue(offerValid);
    }
    
    @Test
    @DisplayName("Offer invalid - target not equal to datasetId")
    public void valiadateOffer_dataset( ) {
        Offer offer = Offer.Builder.newInstance()
        		.id("urn:offer_id")
                .target("invalid_dataset_id")
                .permission(Arrays.asList(MockObjectUtil.PERMISSION).stream().collect(Collectors.toCollection(HashSet::new)))
                .build();
        
    	when(repository.findAll()).thenReturn(new ArrayList<>(MockObjectUtil.CATALOGS));
    	
    	boolean offerValid = service.validateOffer(offer);
    
    	assertFalse(offerValid);
    }
    
    @Test
    @DisplayName("Offer invalid - offer not equal")
    public void valiadateOffer_offer( ) {
    	
    	Constraint constraintDatetime = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.DATE_TIME)
                .operator(Operator.GTEQ)
                .rightOperand("5")
                .build();
    	Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Arrays.asList(constraintDatetime).stream().collect(Collectors.toCollection(HashSet::new)))
                .build();
        Offer offer = Offer.Builder.newInstance()
        		.id("urn:offer_id")
                .target(MockObjectUtil.DATASET_ID)
                .permission(Arrays.asList(permission).stream().collect(Collectors.toCollection(HashSet::new)))
                .build();
        
    	when(repository.findAll()).thenReturn(new ArrayList<>(MockObjectUtil.CATALOGS));
    	
    	boolean offerValid = service.validateOffer(offer);
    
    	assertFalse(offerValid);
    }
}