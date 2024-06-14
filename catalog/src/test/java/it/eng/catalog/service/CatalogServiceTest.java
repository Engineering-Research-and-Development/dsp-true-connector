package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

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
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Offer;
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

    @InjectMocks
    private CatalogService service;

    private Catalog catalog = MockObjectUtil.CATALOG;

    @Test
    @DisplayName("Save catalog successfully")
    void saveCatalog_success() {
        when(repository.save(any(Catalog.class))).thenReturn(catalog);
        Catalog savedCatalog = service.saveCatalog(catalog);
        assertNotNull(savedCatalog);
        verify(repository).save(catalog);
    }

    @Test
    @DisplayName("Get catalog successfully")
    void getCatalog_success() {
        when(repository.findAll()).thenReturn(Collections.singletonList(catalog));
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
        when(repository.findById(anyString())).thenReturn(Optional.of(catalog));
        Optional<Catalog> retrievedCatalog = service.getCatalogById(catalog.getId());
        assertTrue(retrievedCatalog.isPresent());
        verify(repository).findById(catalog.getId());
    }

    @Test
    @DisplayName("Get dataset by ID successfully")
    void getDataSetById_success() {
        catalog.getDataset();
        when(repository.findCatalogByDatasetId(anyString())).thenReturn(Optional.of(catalog));
        Dataset retrievedDataset = service.getDataSetById(MockObjectUtil.DATASET.getId());
        assertNotNull(retrievedDataset);
        assertEquals(MockObjectUtil.DATASET.getId(), retrievedDataset.getId());
    }

    @Test
    @DisplayName("Get dataset by ID throws exception when not found")
    void getDataSetById_notFound() {
        when(repository.findCatalogByDatasetId(anyString())).thenReturn(Optional.empty());
        assertThrows(CatalogErrorException.class, () -> service.getDataSetById("datasetId"));
    }

    @Test
    @DisplayName("Delete catalog successfully")
    void deleteCatalog_success() {
        service.deleteCatalog(catalog.getId());
        verify(repository).deleteById(catalog.getId());
    }

    @Test
    @DisplayName("Update catalog successfully")
    void updateCatalog_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(catalog));
        when(repository.save(any(Catalog.class))).thenReturn(catalog);

        Catalog updatedCatalogData = MockObjectUtil.CATALOG_FOR_UPDATE;

        Catalog updatedCatalog = service.updateCatalog(catalog.getId(), updatedCatalogData);
        assertNotNull(updatedCatalog);
        verify(repository).findById(catalog.getId());
        verify(repository).save(any(Catalog.class));
    }


    @Test
    @DisplayName("Update catalog data service after delete successfully")
    void updateCatalogDataServiceAfterDelete_success() {
        DataService dataService = MockObjectUtil.DATA_SERVICE;
        when(repository.findAll()).thenReturn(Collections.singletonList(catalog));
        when(repository.save(any(Catalog.class))).thenReturn(catalog);

        service.updateCatalogDataServiceAfterDelete(dataService);

        verify(repository).save(any(Catalog.class));
    }
    
    @Test
    public void providedOfferExists() {
    	when(repository.findAll()).thenReturn(new ArrayList<>(MockObjectUtil.CATALOGS));
    	ContractNegotationOfferRequestEvent offerRequest = new ContractNegotationOfferRequestEvent(MockObjectUtil.CONSUMER_PID,
    			MockObjectUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.OFFER_WITH_TARGET));
    	service.validateOffer(offerRequest);;
    	
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
}