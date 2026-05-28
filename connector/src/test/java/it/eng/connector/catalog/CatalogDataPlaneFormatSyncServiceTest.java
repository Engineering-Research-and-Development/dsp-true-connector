package it.eng.connector.catalog;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogDataPlaneFormatSyncServiceTest {

    private static final String TENANT_ID = "tenant-a";
    private static final Instant OLDER_TIMESTAMP = Instant.parse("2024-04-20T10:15:30Z");
    private static final Instant NEWER_TIMESTAMP = Instant.parse("2024-04-21T10:15:30Z");

    @Mock
    private DataPlaneRegistrationService dataPlaneRegistrationService;

    @Mock
    private DatasetRepository datasetRepository;

    @Mock
    private DistributionRepository distributionRepository;

    @Mock
    private CatalogRepository catalogRepository;

    @InjectMocks
    private CatalogDataPlaneFormatSyncService service;

    @Test
    @DisplayName("resolveSupportedFormats returns the union of supported transfer types and ignores transport profiles")
    void resolveSupportedFormatsReturnsUnionOfSupportedTransferTypesAndIgnoresTransportProfiles() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of(
                buildRegistration("http://dataplane-1", Set.of("HttpData-PULL", "stream:grpc"), Set.of("profile-a")),
                buildRegistration("http://dataplane-2", Set.of("HttpData-PUSH", "stream:grpc"), Set.of("profile-b"))
        ));

        Set<String> supportedFormats = service.resolveSupportedFormats();

        assertEquals(Set.of("HttpData-PULL", "HttpData-PUSH", "stream:grpc"), supportedFormats);
    }

    @Test
    @DisplayName("reconcileCatalogDistributions materializes supported formats and refreshes catalog references")
    void reconcileCatalogDistributionsMaterializesSupportedFormatsAndRefreshesCatalogReferences() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of(
                buildRegistration("http://dataplane-1", Set.of("HttpData-PULL"), Set.of("profile-a")),
                buildRegistration("http://dataplane-2", Set.of("HttpData-PUSH"), Set.of("profile-b"))
        ));

        Distribution pullDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                        OLDER_TIMESTAMP, OLDER_TIMESTAMP, "older-title"),
                "distribution-pull", 7L);
        Distribution staleDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "UNSUPPORTED",
                        NEWER_TIMESTAMP, NEWER_TIMESTAMP, "latest-title"),
                "distribution-stale", 9L);
        Dataset dataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Set.of(pullDistribution, staleDistribution)));
        Catalog catalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID, Set.of(dataset));

        when(datasetRepository.findAll()).thenReturn(List.of(dataset));
        when(catalogRepository.findAll()).thenReturn(List.of(catalog));
        when(distributionRepository.saveAll(any())).thenAnswer(invocation ->
                toDistributionList(invocation.getArgument(0)));
        when(datasetRepository.saveAll(any())).thenAnswer(invocation ->
                toDatasetList(invocation.getArgument(0)));
        when(catalogRepository.saveAll(any())).thenAnswer(invocation ->
                toCatalogList(invocation.getArgument(0)));

        service.reconcileCatalogDistributions();

        ArgumentCaptor<Iterable<Distribution>> savedDistributionsCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<Iterable<Dataset>> savedDatasetsCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<Iterable<Catalog>> savedCatalogsCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<Iterable<String>> deletedIdsCaptor = ArgumentCaptor.forClass(Iterable.class);

        verify(distributionRepository).saveAll(savedDistributionsCaptor.capture());
        verify(datasetRepository).saveAll(savedDatasetsCaptor.capture());
        verify(catalogRepository).saveAll(savedCatalogsCaptor.capture());
        verify(distributionRepository).deleteAllById(deletedIdsCaptor.capture());

        List<Distribution> savedDistributions = toDistributionList(savedDistributionsCaptor.getValue());
        assertEquals(2, savedDistributions.size());

        Distribution reconciledPull = findDistributionByFormat(savedDistributions, "HttpData-PULL");
        assertEquals("distribution-pull", reconciledPull.getId());
        assertEquals(7L, reconciledPull.getVersion());
        assertEquals("latest-title", reconciledPull.getTitle());

        Distribution reconciledPush = findDistributionByFormat(savedDistributions, "HttpData-PUSH");
        assertNotNull(reconciledPush.getId());
        assertNotEquals("distribution-pull", reconciledPush.getId());
        assertNotEquals("distribution-stale", reconciledPush.getId());
        assertEquals("latest-title", reconciledPush.getTitle());
        assertEquals(NEWER_TIMESTAMP, reconciledPush.getModified());

        Dataset savedDataset = toDatasetList(savedDatasetsCaptor.getValue()).get(0);
        assertEquals(Set.of("HttpData-PULL", "HttpData-PUSH"), extractFormats(savedDataset.getDistribution()));

        Catalog savedCatalog = toCatalogList(savedCatalogsCaptor.getValue()).get(0);
        assertEquals(Set.of("HttpData-PULL", "HttpData-PUSH"), extractFormats(savedCatalog.getDistribution()));
        assertFalse(extractFormats(savedCatalog.getDistribution()).contains("UNSUPPORTED"));

        List<String> deletedIds = toStringList(deletedIdsCaptor.getValue());
        assertEquals(List.of("distribution-stale"), deletedIds);
    }

    @Test
    @DisplayName("reconcileCatalogDistributions is a no-op when no dataplane formats are registered")
    void reconcileCatalogDistributionsDoesNothingWhenNoDataplaneFormatsAreRegistered() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of());

        service.reconcileCatalogDistributions();

        verify(datasetRepository, never()).findAll();
        verify(distributionRepository, never()).saveAll(any());
        verify(datasetRepository, never()).saveAll(any());
        verify(catalogRepository, never()).findAll();
        verify(catalogRepository, never()).saveAll(any());
        verify(distributionRepository, never()).deleteAllById(any());
    }

    @Test
    @DisplayName("reconcileCatalogDistributions is transactional to keep catalog state consistent")
    void reconcileCatalogDistributionsIsTransactionalToKeepCatalogStateConsistent() throws NoSuchMethodException {
        assertTrue(CatalogDataPlaneFormatSyncService.class
                        .getMethod("reconcileCatalogDistributions")
                        .isAnnotationPresent(Transactional.class));
    }

    private DataPlaneRegistration buildRegistration(String endpoint, Set<String> supportedTransferTypes,
                                                    Set<String> transportProfiles) {
        return DataPlaneRegistration.Builder.newInstance()
                .endpoint(endpoint)
                .supportedTransferTypes(supportedTransferTypes)
                .transportProfiles(transportProfiles)
                .build();
    }

    private Distribution withIdentity(Distribution distribution, String id, Long version) {
        return Distribution.Builder.newInstance()
                .id(id)
                .tenantId(distribution.getTenantId())
                .createdBy(distribution.getCreatedBy())
                .lastModifiedBy(distribution.getLastModifiedBy())
                .version(version)
                .title(distribution.getTitle())
                .description(distribution.getDescription())
                .issued(distribution.getIssued())
                .modified(distribution.getModified())
                .hasPolicy(distribution.getHasPolicy())
                .format(distribution.getFormat())
                .accessService(distribution.getAccessService())
                .build();
    }

    private Distribution findDistributionByFormat(List<Distribution> distributions, String format) {
        return distributions.stream()
                .filter(distribution -> Objects.equals(format, distribution.getFormat()))
                .findFirst()
                .orElseThrow();
    }

    private Set<String> extractFormats(Set<Distribution> distributions) {
        return distributions.stream()
                .map(Distribution::getFormat)
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<Distribution> toDistributionList(Iterable<Distribution> distributions) {
        List<Distribution> values = new ArrayList<>();
        distributions.forEach(values::add);
        values.sort(Comparator.comparing(Distribution::getFormat));
        return values;
    }

    private List<Dataset> toDatasetList(Iterable<Dataset> datasets) {
        List<Dataset> values = new ArrayList<>();
        datasets.forEach(values::add);
        return values;
    }

    private List<Catalog> toCatalogList(Iterable<Catalog> catalogs) {
        List<Catalog> values = new ArrayList<>();
        catalogs.forEach(values::add);
        return values;
    }

    private List<String> toStringList(Iterable<String> values) {
        List<String> result = new ArrayList<>();
        values.forEach(result::add);
        result.sort(String::compareTo);
        return result;
    }
}
