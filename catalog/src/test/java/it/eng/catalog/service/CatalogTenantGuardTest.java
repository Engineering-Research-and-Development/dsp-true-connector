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
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.service.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogTenantGuardTest {

    private static final String OWN_TENANT = "tenant-a";
    private static final String OTHER_TENANT = "tenant-b";

    @Mock
    private CatalogRepository catalogRepository;
    @Mock
    private DatasetRepository datasetRepository;
    @Mock
    private DataServiceRepository dataServiceRepository;
    @Mock
    private DistributionRepository distributionRepository;

    @InjectMocks
    private CatalogTenantGuard guard;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(OWN_TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private static DataService dataService(String tenantId) {
        return DataService.Builder.newInstance().title("service").tenantId(tenantId).build();
    }

    private static Distribution distribution(String tenantId, DataService accessService) {
        return Distribution.Builder.newInstance()
                .title("distribution")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .accessService(accessService)
                .tenantId(tenantId)
                .build();
    }

    private static Dataset dataset(String tenantId, Set<Distribution> distributions) {
        return Dataset.Builder.newInstance()
                .title("dataset")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .distribution(distributions)
                .tenantId(tenantId)
                .build();
    }

    // ---------------------------------------------------------------- data service

    @Test
    @DisplayName("New data service with a fresh id is accepted")
    void newDataService_freshId_accepted() {
        DataService body = dataService(null);
        when(dataServiceRepository.findById(body.getId())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> guard.checkNewDataService(body));
    }

    @Test
    @DisplayName("New data service reusing an id of the own tenant is accepted")
    void newDataService_ownTenantId_accepted() {
        DataService stored = dataService(OWN_TENANT);
        when(dataServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertDoesNotThrow(() -> guard.checkNewDataService(stored));
    }

    @Test
    @DisplayName("New data service reusing an id of another tenant is rejected without naming that tenant")
    void newDataService_otherTenantId_rejected() {
        DataService stored = dataService(OTHER_TENANT);
        when(dataServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        CatalogErrorAPIException ex = assertThrows(CatalogErrorAPIException.class, () -> guard.checkNewDataService(stored));
        assertFalse(ex.getMessage().contains(OTHER_TENANT));
    }

    @Test
    @DisplayName("New data service reusing an id of a document without tenant is rejected")
    void newDataService_noTenantId_rejected() {
        DataService stored = dataService(null);
        when(dataServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThrows(CatalogErrorAPIException.class, () -> guard.checkNewDataService(stored));
    }

    @Test
    @DisplayName("Without a tenant context no check is made")
    void noTenantContext_noCheck() {
        TenantContextHolder.clear();
        DataService stored = dataService(OTHER_TENANT);
        Catalog catalog = Catalog.Builder.newInstance()
                .title("catalog")
                .service(Set.of(stored))
                .build();

        assertDoesNotThrow(() -> guard.checkNewDataService(stored));
        assertDoesNotThrow(() -> guard.checkCatalogReferences(catalog));
    }

    // ---------------------------------------------------------------- distribution

    @Test
    @DisplayName("New distribution with an access service of the own tenant is accepted")
    void newDistribution_ownAccessService_accepted() {
        DataService service = dataService(OWN_TENANT);
        Distribution body = distribution(null, service);
        when(distributionRepository.findById(body.getId())).thenReturn(Optional.empty());
        when(dataServiceRepository.findById(service.getId())).thenReturn(Optional.of(service));

        assertDoesNotThrow(() -> guard.checkNewDistribution(body));
    }

    @Test
    @DisplayName("New distribution with an access service id that does not exist is accepted")
    void newDistribution_unknownAccessService_accepted() {
        DataService service = dataService(null);
        Distribution body = distribution(null, service);
        when(distributionRepository.findById(body.getId())).thenReturn(Optional.empty());
        when(dataServiceRepository.findById(service.getId())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> guard.checkNewDistribution(body));
    }

    @Test
    @DisplayName("New distribution with an access service of another tenant is rejected")
    void newDistribution_otherTenantAccessService_rejected() {
        DataService service = dataService(OTHER_TENANT);
        Distribution body = distribution(null, service);
        when(distributionRepository.findById(body.getId())).thenReturn(Optional.empty());
        when(dataServiceRepository.findById(service.getId())).thenReturn(Optional.of(service));

        assertThrows(CatalogErrorAPIException.class, () -> guard.checkNewDistribution(body));
    }

    @Test
    @DisplayName("New distribution reusing an id of another tenant is rejected")
    void newDistribution_otherTenantId_rejected() {
        Distribution stored = distribution(OTHER_TENANT, dataService(OTHER_TENANT));
        when(distributionRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThrows(CatalogErrorAPIException.class, () -> guard.checkNewDistribution(stored));
    }

    @Test
    @DisplayName("Distribution update with an access service of another tenant is rejected")
    void distributionUpdate_otherTenantAccessService_rejected() {
        DataService service = dataService(OTHER_TENANT);
        when(dataServiceRepository.findById(service.getId())).thenReturn(Optional.of(service));

        assertThrows(CatalogErrorAPIException.class,
                () -> guard.checkDistributionReferences(distribution(null, service)));
    }

    // ---------------------------------------------------------------- dataset

    @Test
    @DisplayName("New dataset with distributions of the own tenant is accepted")
    void newDataset_ownDistributions_accepted() {
        Distribution own = distribution(OWN_TENANT, dataService(OWN_TENANT));
        Dataset body = dataset(null, Set.of(own));
        when(datasetRepository.findById(body.getId())).thenReturn(Optional.empty());
        when(distributionRepository.findById(own.getId())).thenReturn(Optional.of(own));

        assertDoesNotThrow(() -> guard.checkNewDataset(body));
    }

    @Test
    @DisplayName("New dataset reusing an id of another tenant is rejected")
    void newDataset_otherTenantId_rejected() {
        Dataset stored = dataset(OTHER_TENANT, null);
        when(datasetRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThrows(CatalogErrorAPIException.class, () -> guard.checkNewDataset(stored));
    }

    @Test
    @DisplayName("Dataset update with a distribution of another tenant is rejected")
    void datasetUpdate_otherTenantDistribution_rejected() {
        Distribution foreign = distribution(OTHER_TENANT, dataService(OTHER_TENANT));
        when(distributionRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(CatalogErrorAPIException.class,
                () -> guard.checkDatasetReferences(dataset(null, Set.of(foreign))));
    }

    @Test
    @DisplayName("Dataset update without a dataset body (artifact only) is accepted")
    void datasetUpdate_nullBody_accepted() {
        assertDoesNotThrow(() -> guard.checkDatasetReferences(null));
        verifyNoInteractions(distributionRepository);
    }

    // ---------------------------------------------------------------- catalog

    @Test
    @DisplayName("New catalog referencing only own documents is accepted")
    void newCatalog_ownReferences_accepted() {
        DataService service = dataService(OWN_TENANT);
        Distribution distribution = distribution(OWN_TENANT, service);
        Dataset dataset = dataset(OWN_TENANT, Set.of(distribution));
        Catalog body = Catalog.Builder.newInstance()
                .title("catalog")
                .dataset(Set.of(dataset))
                .service(Set.of(service))
                .distribution(Set.of(distribution))
                .build();
        when(catalogRepository.findById(body.getId())).thenReturn(Optional.empty());
        when(datasetRepository.findById(dataset.getId())).thenReturn(Optional.of(dataset));
        when(dataServiceRepository.findById(service.getId())).thenReturn(Optional.of(service));
        when(distributionRepository.findById(distribution.getId())).thenReturn(Optional.of(distribution));

        assertDoesNotThrow(() -> guard.checkNewCatalog(body));
    }

    @Test
    @DisplayName("New catalog reusing an id of another tenant is rejected")
    void newCatalog_otherTenantId_rejected() {
        Catalog stored = Catalog.Builder.newInstance().title("catalog").tenantId(OTHER_TENANT).build();
        when(catalogRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThrows(CatalogErrorAPIException.class, () -> guard.checkNewCatalog(stored));
    }

    @Test
    @DisplayName("Catalog referencing a dataset of another tenant is rejected")
    void catalog_otherTenantDataset_rejected() {
        Dataset foreign = dataset(OTHER_TENANT, null);
        when(datasetRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(CatalogErrorAPIException.class, () -> guard.checkCatalogReferences(
                Catalog.Builder.newInstance().title("catalog").dataset(Set.of(foreign)).build()));
    }

    @Test
    @DisplayName("Catalog referencing a data service of another tenant is rejected")
    void catalog_otherTenantDataService_rejected() {
        DataService foreign = dataService(OTHER_TENANT);
        when(dataServiceRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(CatalogErrorAPIException.class, () -> guard.checkCatalogReferences(
                Catalog.Builder.newInstance().title("catalog").service(Set.of(foreign)).build()));
    }

    @Test
    @DisplayName("Catalog referencing a distribution of another tenant is rejected")
    void catalog_otherTenantDistribution_rejected() {
        Distribution foreign = distribution(OTHER_TENANT, dataService(OTHER_TENANT));
        when(distributionRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(CatalogErrorAPIException.class, () -> guard.checkCatalogReferences(
                Catalog.Builder.newInstance().title("catalog").distribution(Set.of(foreign)).build()));
    }

    @Test
    @DisplayName("Catalog references to ids that do not exist are accepted")
    void catalog_unknownReferences_accepted() {
        when(datasetRepository.findById(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> guard.checkCatalogReferences(
                Catalog.Builder.newInstance().title("catalog").dataset(Set.of(dataset(null, null))).build()));
    }
}
