package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.model.*;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantBucketResolver;
import it.eng.tools.service.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceTest {

    private static final String BUCKET_NAME = "bucket-name";
    private static final String TENANT_ID = "engineering";

    private Catalog catalog;

    @Mock
    private CatalogRepository repository;
    @Mock
    private AuditEventPublisher publisher;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private TenantBucketResolver tenantBucketResolver;


    @Captor
    private ArgumentCaptor<Catalog> argCaptorCatalog;

    @InjectMocks
    private CatalogService service;

    @BeforeEach
    public void setUp() {
        catalog = CatalogMockObjectUtil.createNewCatalog();
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Save catalog successfully")
    public void saveCatalog_success() {
        when(repository.save(any(Catalog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Catalog savedCatalog = service.saveCatalog(catalog);
        assertNotNull(savedCatalog);
        verify(repository).save(argCaptorCatalog.capture());
        assertEquals(catalog.getDataset().stream().findFirst().orElseThrow().getDistribution(),
                argCaptorCatalog.getValue().getDistribution());
        assertEquals(argCaptorCatalog.getValue().getDistribution(), savedCatalog.getDistribution());
    }

    @Test
    @DisplayName("Save catalog normalizes top-level services from dataset distributions")
    public void saveCatalog_normalizesTopLevelServicesFromDatasetDistributions() {
        DataService datasetService = CatalogMockObjectUtil.createNewDataService(TENANT_ID);
        Distribution datasetDistribution = distributionWithAccessService(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                        CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "dataset-title"),
                datasetService);
        Dataset dataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(datasetDistribution)));
        Catalog staleCatalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID, new HashSet<>(Collections.singleton(dataset)));
        staleCatalog.getService().clear();
        staleCatalog.getService().add(CatalogMockObjectUtil.createNewDataService(TENANT_ID));

        when(repository.save(any(Catalog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveCatalog(staleCatalog);

        verify(repository).save(argCaptorCatalog.capture());
        assertEquals(Set.of(datasetService), argCaptorCatalog.getValue().getService());
    }

    @Test
    @DisplayName("Save catalog preserves explicit top-level references when dataset is missing")
    public void saveCatalog_preservesExplicitTopLevelReferencesWhenDatasetIsMissing() {
        Catalog catalogWithoutDataset = Catalog.Builder.newInstance()
                .id(catalog.getId())
                .conformsTo(catalog.getConformsTo())
                .creator(catalog.getCreator())
                .description(catalog.getDescription())
                .identifier(catalog.getIdentifier())
                .issued(catalog.getIssued())
                .keyword(catalog.getKeyword())
                .modified(catalog.getModified())
                .theme(catalog.getTheme())
                .title(catalog.getTitle())
                .participantId(catalog.getParticipantId())
                .service(catalog.getService())
                .distribution(catalog.getDistribution())
                .hasPolicy(catalog.getHasPolicy())
                .build();
        when(repository.save(any(Catalog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Catalog savedCatalog = service.saveCatalog(catalogWithoutDataset);

        assertEquals(catalog.getDistribution(), savedCatalog.getDistribution());
        assertEquals(catalog.getService(), savedCatalog.getService());
        assertNull(savedCatalog.getDataset());
        verify(repository).save(argCaptorCatalog.capture());
        assertEquals(catalog.getDistribution(), argCaptorCatalog.getValue().getDistribution());
        assertEquals(catalog.getService(), argCaptorCatalog.getValue().getService());
        assertNull(argCaptorCatalog.getValue().getDataset());
    }

    @Test
    @DisplayName("Get catalog successfully")
    public void getCatalog_success() {
        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(catalog));
        when(tenantBucketResolver.resolveBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(catalog.getDataset().stream()
                        .map(Dataset::getId).collect(Collectors.toList()));
        Catalog retrievedCatalog = service.getCatalog();
        assertNotNull(retrievedCatalog);
        verify(repository).findAllByTenantId(TENANT_ID);
    }

    @Test
    @DisplayName("Get catalog refreshes top-level distributions from datasets")
    public void getCatalog_refreshesTopLevelDistributionsFromDatasets() {
        DataService datasetService = CatalogMockObjectUtil.createNewDataService(TENANT_ID);
        Distribution datasetDistribution = distributionWithAccessService(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, null,
                        CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "template-title"),
                datasetService);
        Distribution staleCatalogDistribution = CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PUSH",
                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "stale-title");
        Dataset dataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(datasetDistribution)));
        Catalog staleCatalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID, new HashSet<>(Collections.singleton(dataset)));
        staleCatalog.getDistribution().clear();
        staleCatalog.getDistribution().add(staleCatalogDistribution);
        staleCatalog.getService().clear();
        staleCatalog.getService().add(CatalogMockObjectUtil.createNewDataService(TENANT_ID));

        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(staleCatalog));
        when(tenantBucketResolver.resolveBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME)).thenReturn(List.of(dataset.getId()));

        Catalog retrievedCatalog = service.getCatalog();

        assertEquals(1, retrievedCatalog.getDistribution().size());
        assertNull(retrievedCatalog.getDistribution().stream().findFirst().orElseThrow().getFormat());
        assertTrue(retrievedCatalog.getDistribution().stream()
                .noneMatch(distribution -> "HttpData-PUSH".equals(distribution.getFormat())));
        assertEquals(Set.of(datasetService), retrievedCatalog.getService());
    }

    @Test
    @DisplayName("Get catalog for API refreshes top-level distributions from datasets")
    public void getCatalogForApi_refreshesTopLevelDistributionsFromDatasets() {
        DataService datasetService = CatalogMockObjectUtil.createNewDataService(TENANT_ID);
        Distribution datasetDistribution = distributionWithAccessService(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, null,
                        CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "template-title"),
                datasetService);
        Distribution staleCatalogDistribution = CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PUSH",
                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "stale-title");
        Dataset dataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(datasetDistribution)));
        Catalog staleCatalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID, new HashSet<>(Collections.singleton(dataset)));
        staleCatalog.getDistribution().clear();
        staleCatalog.getDistribution().add(staleCatalogDistribution);
        staleCatalog.getService().clear();
        staleCatalog.getService().add(CatalogMockObjectUtil.createNewDataService(TENANT_ID));

        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(staleCatalog));

        Catalog retrievedCatalog = service.getCatalogForApi();

        assertEquals(1, retrievedCatalog.getDistribution().size());
        assertNull(retrievedCatalog.getDistribution().stream().findFirst().orElseThrow().getFormat());
        assertTrue(retrievedCatalog.getDistribution().stream()
                .noneMatch(distribution -> "HttpData-PUSH".equals(distribution.getFormat())));
        assertEquals(Set.of(datasetService), retrievedCatalog.getService());
    }

    @Test
    @DisplayName("Get catalog check if uploading dataset is removed")
    public void getCatalog_checkIfUploadingDatasetIsRemoved() {
        assertFalse(catalog.getDataset().isEmpty());
        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(catalog));
        when(tenantBucketResolver.resolveBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(Collections.emptyList());
        assertThrows(CatalogErrorException.class, () -> service.getCatalog());
    }

    @Test
    @DisplayName("Get catalog throws exception when not found")
    public void getCatalog_notFound() {
        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
        assertThrows(CatalogErrorException.class, () -> service.getCatalog());
    }

    @Test
    @DisplayName("Get catalog by ID successfully")
    public void getCatalogById_success() {
        Distribution datasetDistribution = CatalogMockObjectUtil.createNewDistribution(TENANT_ID, null,
                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "template-title");
        Distribution staleCatalogDistribution = CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PUSH",
                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "stale-title");
        Dataset dataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(datasetDistribution)));
        Catalog staleCatalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID, new HashSet<>(Collections.singleton(dataset)));
        staleCatalog.getDistribution().clear();
        staleCatalog.getDistribution().add(staleCatalogDistribution);

        when(repository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.of(staleCatalog));
        Catalog retrievedCatalog = service.getCatalogById(staleCatalog.getId());

        assertNotNull(retrievedCatalog);
        assertEquals(1, retrievedCatalog.getDistribution().size());
        assertNull(retrievedCatalog.getDistribution().stream().findFirst().orElseThrow().getFormat());
        assertTrue(retrievedCatalog.getDistribution().stream()
                .noneMatch(distribution -> "HttpData-PUSH".equals(distribution.getFormat())));
        assertEquals(Set.of(dataset.getDistribution().stream().findFirst().orElseThrow().getAccessService()),
                retrievedCatalog.getService());
        verify(repository).findByIdAndTenantId(staleCatalog.getId(), TENANT_ID);
    }

    @Test
    @DisplayName("Get catalog for API preserves explicit top-level references when dataset is missing")
    public void getCatalogForApi_preservesExplicitTopLevelReferencesWhenDatasetIsMissing() {
        Catalog catalogWithoutDataset = Catalog.Builder.newInstance()
                .id(catalog.getId())
                .conformsTo(catalog.getConformsTo())
                .creator(catalog.getCreator())
                .description(catalog.getDescription())
                .identifier(catalog.getIdentifier())
                .issued(catalog.getIssued())
                .keyword(catalog.getKeyword())
                .modified(catalog.getModified())
                .theme(catalog.getTheme())
                .title(catalog.getTitle())
                .participantId(catalog.getParticipantId())
                .service(catalog.getService())
                .distribution(catalog.getDistribution())
                .hasPolicy(catalog.getHasPolicy())
                .build();
        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(catalogWithoutDataset));

        Catalog retrievedCatalog = service.getCatalogForApi();

        assertEquals(catalog.getDistribution(), retrievedCatalog.getDistribution());
        assertEquals(catalog.getService(), retrievedCatalog.getService());
        assertNull(retrievedCatalog.getDataset());
    }

    @Test
    @DisplayName("Delete catalog successfully")
    public void deleteCatalog_success() {
        when(repository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.of(catalog));
        service.deleteCatalog(catalog.getId());
        verify(repository).deleteById(catalog.getId());
        verify(publisher).publishEvent(eq(AuditEventType.CATALOG_DELETED), anyString(), any());
    }

    @Test
    @DisplayName("Update catalog successfully")
    public void updateCatalog_success() {
        when(repository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.of(catalog));
        when(repository.save(any(Catalog.class))).thenReturn(CatalogMockObjectUtil.CATALOG_FOR_UPDATE);

        Catalog updatedCatalogData = CatalogMockObjectUtil.CATALOG_FOR_UPDATE;

        Catalog updatedCatalog = service.updateCatalog(catalog.getId(), updatedCatalogData);
        assertNotNull(updatedCatalog);
        verify(repository).findByIdAndTenantId(catalog.getId(), TENANT_ID);
        verify(repository).save(argCaptorCatalog.capture());
        assertTrue(argCaptorCatalog.getValue().getDescription().stream().anyMatch(d -> d.getValue().contains("update")));
        assertTrue(argCaptorCatalog.getValue().getDistribution().stream().anyMatch(d -> d.getTitle().contains("update")));

        assertTrue(argCaptorCatalog.getValue().getDistribution().stream().findFirst().get().getHasPolicy()
                .stream()
                .anyMatch(p -> p.getId().equals("urn:offer_id_update")));


        assertEquals(Set.of(argCaptorCatalog.getValue().getDistribution().stream()
                        .findFirst()
                        .orElseThrow()
                        .getAccessService()),
                argCaptorCatalog.getValue().getService());
    }

    @Test
    @DisplayName("Update catalog normalizes top-level distributions before save")
    public void updateCatalog_normalizesTopLevelDistributionsBeforeSave() {
        Distribution existingDatasetDistribution = CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "existing-title");
        Dataset existingDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(existingDatasetDistribution)));
        Catalog existingCatalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID,
                new HashSet<>(Collections.singleton(existingDataset)));

        Distribution updatedDatasetDistribution = CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PUSH",
                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "updated-title");
        Dataset updatedDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(updatedDatasetDistribution)));
        Catalog updatedCatalogData = Catalog.Builder.newInstance()
                .conformsTo(existingCatalog.getConformsTo())
                .creator(existingCatalog.getCreator())
                .description(existingCatalog.getDescription())
                .identifier(existingCatalog.getIdentifier())
                .issued(existingCatalog.getIssued())
                .keyword(existingCatalog.getKeyword())
                .modified(existingCatalog.getModified())
                .theme(existingCatalog.getTheme())
                .title(existingCatalog.getTitle())
                .participantId(existingCatalog.getParticipantId())
                .service(existingCatalog.getService())
                .dataset(new HashSet<>(Collections.singleton(updatedDataset)))
                .distribution(new HashSet<>(Collections.singleton(
                        CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "STALE-FORMAT",
                                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "stale-title"))))
                .build();

        when(repository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.of(existingCatalog));
        when(repository.save(any(Catalog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Catalog updatedCatalog = service.updateCatalog(existingCatalog.getId(), updatedCatalogData);

        assertEquals(1, updatedCatalog.getDistribution().size());
        assertEquals("HttpData-PUSH", updatedCatalog.getDistribution().stream().findFirst().orElseThrow().getFormat());
        assertTrue(updatedCatalog.getDistribution().stream()
                .noneMatch(distribution -> "STALE-FORMAT".equals(distribution.getFormat())));
        verify(repository).save(argCaptorCatalog.capture());
        assertEquals("HttpData-PUSH", argCaptorCatalog.getValue().getDistribution().stream()
                .findFirst()
                .orElseThrow()
                .getFormat());
    }

    @Test
    @DisplayName("Update catalog normalizes top-level services before save")
    public void updateCatalog_normalizesTopLevelServicesBeforeSave() {
        DataService existingService = CatalogMockObjectUtil.createNewDataService(TENANT_ID);
        Distribution existingDatasetDistribution = distributionWithAccessService(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                        CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "existing-title"),
                existingService);
        Dataset existingDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(existingDatasetDistribution)));
        Catalog existingCatalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID,
                new HashSet<>(Collections.singleton(existingDataset)));

        DataService updatedDatasetService = CatalogMockObjectUtil.createNewDataService(TENANT_ID);
        Distribution updatedDatasetDistribution = distributionWithAccessService(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PUSH",
                        CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "updated-title"),
                updatedDatasetService);
        Dataset updatedDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(updatedDatasetDistribution)));
        Catalog updatedCatalogData = Catalog.Builder.newInstance()
                .conformsTo(existingCatalog.getConformsTo())
                .creator(existingCatalog.getCreator())
                .description(existingCatalog.getDescription())
                .identifier(existingCatalog.getIdentifier())
                .issued(existingCatalog.getIssued())
                .keyword(existingCatalog.getKeyword())
                .modified(existingCatalog.getModified())
                .theme(existingCatalog.getTheme())
                .title(existingCatalog.getTitle())
                .participantId(existingCatalog.getParticipantId())
                .service(new HashSet<>(Collections.singleton(CatalogMockObjectUtil.createNewDataService(TENANT_ID))))
                .dataset(new HashSet<>(Collections.singleton(updatedDataset)))
                .distribution(new HashSet<>(Collections.singleton(
                        CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "STALE-FORMAT",
                                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "stale-title"))))
                .build();

        when(repository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.of(existingCatalog));
        when(repository.save(any(Catalog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Catalog updatedCatalog = service.updateCatalog(existingCatalog.getId(), updatedCatalogData);

        assertEquals(Set.of(updatedDatasetService), updatedCatalog.getService());
        verify(repository).save(argCaptorCatalog.capture());
        assertEquals(Set.of(updatedDatasetService), argCaptorCatalog.getValue().getService());
    }

    @Test
    @DisplayName("Update catalog data service after delete successfully")
    public void updateCatalogDataServiceAfterDelete_success() {

        DataService dataService = CatalogMockObjectUtil.DATA_SERVICE;
        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(catalog));
        when(repository.save(any(Catalog.class))).thenReturn(catalog);

        service.updateCatalogDataServiceAfterDelete(dataService);

        verify(repository).save(any(Catalog.class));
    }

    @Test
    @DisplayName("Update catalog dataset after save normalizes top-level distributions")
    public void updateCatalogDatasetAfterSave_normalizesTopLevelDistributions() {
        Distribution existingDatasetDistribution = CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "existing-title");
        Dataset existingDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(existingDatasetDistribution)));
        Catalog existingCatalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID,
                new HashSet<>(Collections.singleton(existingDataset)));

        Distribution newDatasetDistribution = CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PUSH",
                CatalogMockObjectUtil.ISSUED, CatalogMockObjectUtil.MODIFIED, "new-title");
        Dataset newDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Collections.singleton(newDatasetDistribution)));

        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(existingCatalog));
        when(repository.save(any(Catalog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateCatalogDatasetAfterSave(newDataset);

        verify(repository).save(argCaptorCatalog.capture());
        assertEquals(2, argCaptorCatalog.getValue().getDistribution().size());
        assertTrue(argCaptorCatalog.getValue().getDistribution().stream()
                .anyMatch(distribution -> "HttpData-PULL".equals(distribution.getFormat())));
        assertTrue(argCaptorCatalog.getValue().getDistribution().stream()
                .anyMatch(distribution -> "HttpData-PUSH".equals(distribution.getFormat())));
    }

    @Test
    @DisplayName("updateCatalogDatasetAfterSave rejects cross-tenant dataset with InternalServerErrorAPIException")
    public void updateCatalogDatasetAfterSave_crossTenantDataset_throws() {
        Dataset crossTenantDataset = Dataset.Builder.newInstance()
                .id("urn:dataset:cross-tenant")
                .tenantId("other-tenant")
                .hasPolicy(new HashSet<>())
                .build();

        assertThrows(it.eng.catalog.exceptions.InternalServerErrorAPIException.class,
                () -> service.updateCatalogDatasetAfterSave(crossTenantDataset),
                "A dataset from a different tenant must be rejected");
    }

    @Test
    @DisplayName("updateCatalogDataServiceAfterSave rejects cross-tenant dataService with InternalServerErrorAPIException")
    public void updateCatalogDataServiceAfterSave_crossTenantDataService_throws() {
        DataService crossTenantService = DataService.Builder.newInstance()
                .id("urn:ds:cross-tenant")
                .tenantId("other-tenant")
                .build();

        assertThrows(it.eng.catalog.exceptions.InternalServerErrorAPIException.class,
                () -> service.updateCatalogDataServiceAfterSave(crossTenantService),
                "A data service from a different tenant must be rejected");
    }

    @Test
    @DisplayName("updateCatalogDistributionAfterSave rejects cross-tenant distribution with InternalServerErrorAPIException")
    public void updateCatalogDistributionAfterSave_crossTenantDistribution_throws() {
        Distribution crossTenantDist = Distribution.Builder.newInstance()
                .id("urn:dist:cross-tenant")
                .tenantId("other-tenant")
                .format(CatalogMockObjectUtil.DISTRIBUTION.getFormat())
                .accessService(CatalogMockObjectUtil.DATA_SERVICE)
                .build();

        assertThrows(it.eng.catalog.exceptions.InternalServerErrorAPIException.class,
                () -> service.updateCatalogDistributionAfterSave(crossTenantDist),
                "A distribution from a different tenant must be rejected");
    }


    @Test
    @DisplayName("Offer valid")
    public void validateOffer() {
        Offer offer = Offer.Builder.newInstance()
                .id(catalog.getDataset().stream().findFirst().get().getHasPolicy().stream().findFirst().get().getId())
                .target(catalog.getDataset().stream().findFirst().get().getId())
                .permission(catalog.getDataset().stream().findFirst().get().getHasPolicy().stream().findFirst().get().getPermission())
                .build();

        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(catalog));
        when(tenantBucketResolver.resolveBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(catalog.getDataset().stream()
                        .map(Dataset::getId).collect(Collectors.toList()));

        boolean offerValid = service.validateOffer(offer);

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

        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(catalog));
        when(tenantBucketResolver.resolveBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(catalog.getDataset().stream()
                        .map(Dataset::getId).collect(Collectors.toList()));

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

        when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.singletonList(catalog));
        when(tenantBucketResolver.resolveBucketName()).thenReturn(BUCKET_NAME);
        when(s3ClientService.listFiles(BUCKET_NAME))
                .thenReturn(catalog.getDataset().stream()
                        .map(Dataset::getId).collect(Collectors.toList()));

        boolean offerValid = service.validateOffer(offer);

        assertFalse(offerValid);
    }

    private Distribution distributionWithAccessService(Distribution distribution, DataService accessService) {
        return Distribution.Builder.newInstance()
                .id(distribution.getId())
                .tenantId(distribution.getTenantId())
                .createdBy(distribution.getCreatedBy())
                .lastModifiedBy(distribution.getLastModifiedBy())
                .version(distribution.getVersion())
                .title(distribution.getTitle())
                .description(distribution.getDescription())
                .issued(distribution.getIssued())
                .modified(distribution.getModified())
                .hasPolicy(distribution.getHasPolicy())
                .format(distribution.getFormat())
                .accessService(accessService)
                .build();
    }
}
