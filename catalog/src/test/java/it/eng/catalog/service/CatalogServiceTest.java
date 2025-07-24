package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.model.*;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceTest {

    private static final String BUCKET_NAME = "bucket-name";
    @Mock
    private CatalogRepository repository;
    @Mock
    private AuditEventPublisher publisher;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private S3ClientService s3ClientService;

    @Captor
    private ArgumentCaptor<ContractNegotiationOfferResponseEvent> argCaptorContractNegotiationOfferResponse;

    @Captor
    private ArgumentCaptor<Catalog> argCaptorCatalog;

    @InjectMocks
    private CatalogService service;

    @Test
    @DisplayName("Save catalog successfully")
    void saveCatalog_success() {
        when(repository.save(any(Catalog.class))).thenReturn(CatalogMockObjectUtil.CATALOG);
        Catalog savedCatalog = service.saveCatalog(CatalogMockObjectUtil.CATALOG);
        assertNotNull(savedCatalog);
        verify(repository).save(CatalogMockObjectUtil.CATALOG);
    }

    @Test
    @DisplayName("Get catalog successfully")
    void getCatalog_success() {
        when(repository.findAll()).thenReturn(Collections.singletonList(CatalogMockObjectUtil.CATALOG));
        when(s3Properties.getBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(CatalogMockObjectUtil.CATALOG.getDataset().stream()
                        .map(Dataset::getId).collect(Collectors.toList()));
        Catalog retrievedCatalog = service.getCatalog();
        assertNotNull(retrievedCatalog);
        verify(repository).findAll();
    }

    @Test
    @DisplayName("Get catalog check if uploading dataset is removed")
    void getCatalog_checkIfUploadingDatasetIsRemoved() {
        assertFalse(CatalogMockObjectUtil.CATALOG.getDataset().isEmpty());
        when(repository.findAll()).thenReturn(Collections.singletonList(CatalogMockObjectUtil.CATALOG));
        when(s3Properties.getBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(Collections.emptyList());
        Catalog retrievedCatalog = service.getCatalog();
        assertNotNull(retrievedCatalog);
        assertTrue(retrievedCatalog.getDataset().isEmpty());
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
        when(repository.findById(anyString())).thenReturn(Optional.of(CatalogMockObjectUtil.CATALOG));
        Catalog retrievedCatalog = service.getCatalogById(CatalogMockObjectUtil.CATALOG.getId());
        assertNotNull(retrievedCatalog);
        verify(repository).findById(CatalogMockObjectUtil.CATALOG.getId());
    }

    @Test
    @DisplayName("Delete catalog successfully")
    void deleteCatalog_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(CatalogMockObjectUtil.CATALOG));
        service.deleteCatalog(CatalogMockObjectUtil.CATALOG.getId());
        verify(repository).deleteById(CatalogMockObjectUtil.CATALOG.getId());
    }

    @Test
    @DisplayName("Update catalog successfully")
    void updateCatalog_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(CatalogMockObjectUtil.CATALOG));
        when(repository.save(any(Catalog.class))).thenReturn(CatalogMockObjectUtil.CATALOG_FOR_UPDATE);

        Catalog updatedCatalogData = CatalogMockObjectUtil.CATALOG_FOR_UPDATE;

        Catalog updatedCatalog = service.updateCatalog(CatalogMockObjectUtil.CATALOG.getId(), updatedCatalogData);
        assertNotNull(updatedCatalog);
        verify(repository).findById(CatalogMockObjectUtil.CATALOG.getId());
        verify(repository).save(argCaptorCatalog.capture());
        assertTrue(argCaptorCatalog.getValue().getDescription().stream().anyMatch(d -> d.getValue().contains("update")));
        assertTrue(argCaptorCatalog.getValue().getDistribution().stream().anyMatch(d -> d.getTitle().contains("update")));

        assertTrue(argCaptorCatalog.getValue().getDistribution().stream().findFirst().get().getHasPolicy()
                .stream()
                .anyMatch(p -> p.getId().equals("urn:offer_id_update")));


        assertTrue(argCaptorCatalog.getValue().getService().stream()
                .anyMatch(s -> s.getCreator().contains("update")
                        && s.getEndpointURL().contains("update")
                        && s.getEndpointDescription().contains("update")));
    }

    @Test
    @DisplayName("Update catalog data service after delete successfully")
    void updateCatalogDataServiceAfterDelete_success() {

        DataService dataService = CatalogMockObjectUtil.DATA_SERVICE;
        when(repository.findAll()).thenReturn(Collections.singletonList(CatalogMockObjectUtil.CATALOG));
        when(repository.save(any(Catalog.class))).thenReturn(CatalogMockObjectUtil.CATALOG);

        service.updateCatalogDataServiceAfterDelete(dataService);

        verify(repository).save(any(Catalog.class));
    }

    @Test
    public void providedOfferExists() {
        Catalog catalog = CatalogMockObjectUtil.createNewCatalog();
        when(repository.findAll()).thenReturn(List.of(catalog));
        when(s3Properties.getBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(catalog.getDataset().stream()
                        .map(Dataset::getId).collect(Collectors.toList()));
        ContractNegotationOfferRequestEvent offerRequest = new ContractNegotationOfferRequestEvent(CatalogMockObjectUtil.CONSUMER_PID,
                CatalogMockObjectUtil.PROVIDER_PID, CatalogSerializer.serializeProtocolJsonNode(CatalogMockObjectUtil.OFFER_WITH_TARGET));
        service.validateOffer(offerRequest);

        verify(publisher).publishEvent(argCaptorContractNegotiationOfferResponse.capture());
        assertTrue(argCaptorContractNegotiationOfferResponse.getValue().isOfferAccepted());
    }

    @Test
    public void providedOfferNotFound() {
        Offer differentOffer = Offer.Builder.newInstance()
                .id("urn:offer_id")
                .target(CatalogMockObjectUtil.TARGET)
                .permission(Set.of(CatalogMockObjectUtil.PERMISSION_ANONYMIZE))
                .build();

        when(repository.findAll()).thenReturn(new ArrayList<>(CatalogMockObjectUtil.CATALOGS));
        ContractNegotationOfferRequestEvent offerRequest = new ContractNegotationOfferRequestEvent(CatalogMockObjectUtil.CONSUMER_PID,
                CatalogMockObjectUtil.PROVIDER_PID, CatalogSerializer.serializeProtocolJsonNode(differentOffer));
        service.validateOffer(offerRequest);

        verify(publisher).publishEvent(argCaptorContractNegotiationOfferResponse.capture());
        assertFalse(argCaptorContractNegotiationOfferResponse.getValue().isOfferAccepted());
    }

    @Test
    @DisplayName("Offer valid")
    public void validateOffer() {
        when(repository.findAll()).thenReturn(new ArrayList<>(CatalogMockObjectUtil.CATALOGS));
        when(s3Properties.getBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(CatalogMockObjectUtil.CATALOG.getDataset().stream()
                        .map(Dataset::getId).collect(Collectors.toList()));

        boolean offerValid = service.validateOffer(CatalogMockObjectUtil.OFFER_WITH_TARGET);

        assertTrue(offerValid);
    }

    @Test
    @DisplayName("Offer invalid - target not equal to datasetId")
    public void validateOffer_dataset() {
        Offer offer = Offer.Builder.newInstance()
                .id("urn:offer_id")
                .target("invalid_dataset_id")
                .permission(new HashSet<>(Collections.singletonList(CatalogMockObjectUtil.PERMISSION)))
                .build();

        when(repository.findAll()).thenReturn(new ArrayList<>(CatalogMockObjectUtil.CATALOGS));

        boolean offerValid = service.validateOffer(offer);

        assertFalse(offerValid);
    }

    @Test
    @DisplayName("Offer invalid - offer not equal")
    public void validateOffer_offer() {

        Constraint constraintDatetime = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.DATE_TIME)
                .operator(Operator.GTEQ)
                .rightOperand("5")
                .build();
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(new HashSet<>(Collections.singletonList(constraintDatetime)))
                .build();
        Offer offer = Offer.Builder.newInstance()
                .id("urn:offer_id")
                .target(CatalogMockObjectUtil.DATASET_ID)
                .permission(new HashSet<>(Collections.singletonList(permission)))
                .build();

        when(repository.findAll()).thenReturn(new ArrayList<>(CatalogMockObjectUtil.CATALOGS));

        boolean offerValid = service.validateOffer(offer);

        assertFalse(offerValid);
    }
}