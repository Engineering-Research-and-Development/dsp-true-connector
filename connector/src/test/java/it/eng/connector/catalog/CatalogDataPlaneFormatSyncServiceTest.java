package it.eng.connector.catalog;

import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @DisplayName("reconcileCatalogDistributions materializes supported formats without persisting catalog distributions")
    void reconcileCatalogDistributionsMaterializesSupportedFormatsWithoutPersistingCatalogDistributions() {
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
        when(datasetRepository.findAll()).thenReturn(List.of(dataset));
        when(distributionRepository.saveAll(any())).thenAnswer(invocation ->
                toDistributionList(invocation.getArgument(0)));
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileCatalogDistributions();

        ArgumentCaptor<Iterable<Distribution>> savedDistributionsCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<Dataset> savedDatasetCaptor = ArgumentCaptor.forClass(Dataset.class);
        ArgumentCaptor<Iterable<String>> deletedIdsCaptor = ArgumentCaptor.forClass(Iterable.class);

        verify(distributionRepository).saveAll(savedDistributionsCaptor.capture());
        verify(datasetRepository).save(savedDatasetCaptor.capture());
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

        Dataset savedDataset = savedDatasetCaptor.getValue();
        assertEquals(Set.of("HttpData-PULL", "HttpData-PUSH"), extractFormats(savedDataset.getDistribution()));
        List<String> deletedIds = toStringList(deletedIdsCaptor.getValue());
        assertEquals(List.of("distribution-stale"), deletedIds);
    }

    @Test
    @DisplayName("reconcileCatalogDistributions keeps one template distribution when no dataplane formats are registered")
    void reconcileCatalogDistributionsKeepsOneTemplateDistributionWhenNoDataplaneFormatsAreRegistered() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of());

        Distribution olderDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                        OLDER_TIMESTAMP, OLDER_TIMESTAMP, "older-title"),
                "distribution-pull", 7L);
        Distribution newerDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PUSH",
                        NEWER_TIMESTAMP, NEWER_TIMESTAMP, "latest-title"),
                "distribution-push", 9L);
        Dataset dataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new HashSet<>(Set.of(olderDistribution, newerDistribution)));
        when(datasetRepository.findAll()).thenReturn(List.of(dataset));
        when(distributionRepository.saveAll(any())).thenAnswer(invocation ->
                toDistributionList(invocation.getArgument(0)));
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileCatalogDistributions();

        ArgumentCaptor<Iterable<Distribution>> savedDistributionsCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<Dataset> savedDatasetCaptor = ArgumentCaptor.forClass(Dataset.class);
        ArgumentCaptor<Iterable<String>> deletedIdsCaptor = ArgumentCaptor.forClass(Iterable.class);

        verify(datasetRepository).findAll();
        verify(distributionRepository).saveAll(savedDistributionsCaptor.capture());
        verify(datasetRepository).save(savedDatasetCaptor.capture());
        verify(distributionRepository).deleteAllById(deletedIdsCaptor.capture());

        List<Distribution> savedDistributions = toDistributionList(savedDistributionsCaptor.getValue());
        assertEquals(1, savedDistributions.size());
        Distribution savedDistribution = savedDistributions.get(0);
        assertEquals("distribution-push", savedDistribution.getId());
        assertEquals(9L, savedDistribution.getVersion());
        assertEquals("latest-title", savedDistribution.getTitle());
        assertEquals(NEWER_TIMESTAMP, savedDistribution.getModified());
        assertNull(savedDistribution.getFormat());

        Dataset savedDataset = savedDatasetCaptor.getValue();
        assertEquals(1, savedDataset.getDistribution().size());
        assertNull(savedDataset.getDistribution().stream().findFirst().orElseThrow().getFormat());

        List<String> deletedIds = toStringList(deletedIdsCaptor.getValue());
        assertEquals(List.of("distribution-pull"), deletedIds);
    }

    @Test
    @DisplayName("reconcileCatalogDistributions persists and deletes per dataset in order")
    void reconcileCatalogDistributionsPersistsAndDeletesPerDatasetInOrder() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of(
                buildRegistration("http://dataplane-1", Set.of("HttpData-PULL", "HttpData-PUSH"), Set.of("profile-a"))
        ));

        Dataset firstDataset = buildDataset("dataset-1", "distribution-1-pull", "distribution-1-stale",
                "first-latest-title");
        Dataset secondDataset = buildDataset("dataset-2", "distribution-2-pull", "distribution-2-stale",
                "second-latest-title");
        when(datasetRepository.findAll()).thenReturn(List.of(firstDataset, secondDataset));
        when(distributionRepository.saveAll(any())).thenAnswer(invocation ->
                toDistributionList(invocation.getArgument(0)));
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileCatalogDistributions();

        InOrder inOrder = inOrder(distributionRepository, datasetRepository);
        inOrder.verify(distributionRepository).saveAll(argThat(distributions ->
                containsDistributionId(distributions, "distribution-1-pull")));
        inOrder.verify(datasetRepository).save(argThat(dataset ->
                Objects.equals("dataset-1", dataset.getId())));
        inOrder.verify(distributionRepository).deleteAllById(argThat(ids ->
                containsString(ids, "distribution-1-stale")));
        inOrder.verify(distributionRepository).saveAll(argThat(distributions ->
                containsDistributionId(distributions, "distribution-2-pull")));
        inOrder.verify(datasetRepository).save(argThat(dataset ->
                Objects.equals("dataset-2", dataset.getId())));
        inOrder.verify(distributionRepository).deleteAllById(argThat(ids ->
                containsString(ids, "distribution-2-stale")));
    }

    @Test
    @DisplayName("reconcileTenant scopes dataset reconciliation to the requested tenant")
    void reconcileTenantScopesDatasetReconciliationToTheRequestedTenant() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of(
                buildRegistration("http://dataplane-1", Set.of("HttpData-PULL", "HttpData-PUSH"), Set.of("profile-a"))
        ));

        Dataset tenantDataset = buildDataset("dataset-tenant", "distribution-tenant-pull", "distribution-tenant-stale",
                "tenant-latest-title");
        when(datasetRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(tenantDataset));
        when(distributionRepository.saveAll(any())).thenAnswer(invocation ->
                toDistributionList(invocation.getArgument(0)));
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileTenant(TENANT_ID);

        verify(datasetRepository).findAllByTenantId(TENANT_ID);
        verify(datasetRepository).findAll();
        verify(datasetRepository).save(argThat(dataset ->
                Objects.equals("dataset-tenant", dataset.getId())
                        && extractFormats(dataset.getDistribution()).equals(Set.of("HttpData-PULL", "HttpData-PUSH"))));
        verify(distributionRepository).deleteAllById(argThat(ids ->
                containsString(ids, "distribution-tenant-stale")));
    }

    @Test
    @DisplayName("reconcileTenant clones globally shared distributions without deleting cross-tenant references")
    void reconcileTenantClonesGloballySharedDistributionsWithoutDeletingCrossTenantReferences() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of(
                buildRegistration("http://dataplane-1", Set.of("HttpData-PULL", "HttpData-PUSH"), Set.of("profile-a"))
        ));

        Distribution sharedPullDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                        OLDER_TIMESTAMP, OLDER_TIMESTAMP, "shared-title"),
                "distribution-shared-pull", 7L);
        Dataset tenantDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new LinkedHashSet<>(List.of(
                        sharedPullDistribution,
                        withIdentity(CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "UNSUPPORTED",
                                NEWER_TIMESTAMP, NEWER_TIMESTAMP, "tenant-template-title"),
                                "distribution-tenant-stale", 9L)
                )));
        Dataset otherTenantDataset = CatalogMockObjectUtil.createNewDataset("tenant-b",
                new LinkedHashSet<>(Set.of(sharedPullDistribution)));
        when(datasetRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(tenantDataset));
        when(datasetRepository.findAll()).thenReturn(List.of(tenantDataset, otherTenantDataset));
        when(distributionRepository.saveAll(any())).thenAnswer(invocation ->
                toDistributionList(invocation.getArgument(0)));
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileTenant(TENANT_ID);

        ArgumentCaptor<Dataset> savedDatasetCaptor = ArgumentCaptor.forClass(Dataset.class);
        ArgumentCaptor<Iterable<String>> deletedIdsCaptor = ArgumentCaptor.forClass(Iterable.class);

        verify(datasetRepository).findAllByTenantId(TENANT_ID);
        verify(datasetRepository).findAll();
        verify(datasetRepository).save(savedDatasetCaptor.capture());
        verify(distributionRepository).deleteAllById(deletedIdsCaptor.capture());

        Distribution savedPullDistribution = findDistributionByFormat(
                new ArrayList<>(savedDatasetCaptor.getValue().getDistribution()), "HttpData-PULL");
        assertNotEquals("distribution-shared-pull", savedPullDistribution.getId());
        assertNull(savedPullDistribution.getVersion());

        List<String> deletedIds = toStringList(deletedIdsCaptor.getValue());
        assertEquals(List.of("distribution-tenant-stale"), deletedIds);
    }

    @Test
    @DisplayName("reconcileCatalogDistributions breaks template ties by distribution id")
    void reconcileCatalogDistributionsBreaksTemplateTiesByDistributionId() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of());

        Instant timestamp = Instant.parse("2024-04-22T10:15:30Z");
        Distribution firstDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                        timestamp, timestamp, "first-title"),
                "distribution-a", 7L);
        Distribution secondDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PUSH",
                        timestamp, timestamp, "second-title"),
                "distribution-z", 9L);
        Dataset dataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new LinkedHashSet<>(List.of(firstDistribution, secondDistribution)));
        when(datasetRepository.findAll()).thenReturn(List.of(dataset));
        when(distributionRepository.saveAll(any())).thenAnswer(invocation ->
                toDistributionList(invocation.getArgument(0)));
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileCatalogDistributions();

        ArgumentCaptor<Iterable<Distribution>> savedDistributionsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(distributionRepository).saveAll(savedDistributionsCaptor.capture());
        Distribution savedDistribution = toDistributionList(savedDistributionsCaptor.getValue()).get(0);
        assertEquals("distribution-z", savedDistribution.getId());
        assertEquals("second-title", savedDistribution.getTitle());
    }

    @Test
    @DisplayName("reconcileCatalogDistributions clones shared distribution references per dataset")
    void reconcileCatalogDistributionsClonesSharedDistributionReferencesPerDataset() {
        when(dataPlaneRegistrationService.findAll()).thenReturn(List.of(
                buildRegistration("http://dataplane-1", Set.of("HttpData-PULL", "HttpData-PUSH"), Set.of("profile-a"))
        ));

        Distribution sharedPullDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                        OLDER_TIMESTAMP, OLDER_TIMESTAMP, "shared-title"),
                "distribution-shared-pull", 7L);
        Dataset firstDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new LinkedHashSet<>(List.of(
                        sharedPullDistribution,
                        withIdentity(CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "UNSUPPORTED",
                                NEWER_TIMESTAMP, NEWER_TIMESTAMP, "first-template-title"),
                                "distribution-first-stale", 9L)
                )));
        Dataset secondDataset = CatalogMockObjectUtil.createNewDataset(TENANT_ID,
                new LinkedHashSet<>(List.of(
                        sharedPullDistribution,
                        withIdentity(CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "UNSUPPORTED",
                                NEWER_TIMESTAMP, NEWER_TIMESTAMP, "second-template-title"),
                                "distribution-second-stale", 11L)
                )));
        when(datasetRepository.findAll()).thenReturn(List.of(firstDataset, secondDataset));
        when(distributionRepository.saveAll(any())).thenAnswer(invocation ->
                toDistributionList(invocation.getArgument(0)));
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileCatalogDistributions();

        ArgumentCaptor<Dataset> savedDatasetCaptor = ArgumentCaptor.forClass(Dataset.class);
        verify(datasetRepository, times(2)).save(savedDatasetCaptor.capture());

        List<Dataset> savedDatasets = savedDatasetCaptor.getAllValues();
        Distribution firstSavedPullDistribution = findDistributionByFormat(
                new ArrayList<>(savedDatasets.get(0).getDistribution()), "HttpData-PULL");
        Distribution secondSavedPullDistribution = findDistributionByFormat(
                new ArrayList<>(savedDatasets.get(1).getDistribution()), "HttpData-PULL");

        assertNotEquals("distribution-shared-pull", firstSavedPullDistribution.getId());
        assertNotEquals("distribution-shared-pull", secondSavedPullDistribution.getId());
        assertNotEquals(firstSavedPullDistribution.getId(), secondSavedPullDistribution.getId());
        assertNull(firstSavedPullDistribution.getVersion());
        assertNull(secondSavedPullDistribution.getVersion());
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

    private Dataset buildDataset(String datasetId, String pullDistributionId, String staleDistributionId, String latestTitle) {
        Distribution pullDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "HttpData-PULL",
                        OLDER_TIMESTAMP, OLDER_TIMESTAMP, "older-title"),
                pullDistributionId, 7L);
        Distribution staleDistribution = withIdentity(
                CatalogMockObjectUtil.createNewDistribution(TENANT_ID, "UNSUPPORTED",
                        NEWER_TIMESTAMP, NEWER_TIMESTAMP, latestTitle),
                staleDistributionId, 9L);
        return Dataset.Builder.newInstance()
                .id(datasetId)
                .keyword(Set.of("keyword"))
                .theme(Set.of("theme"))
                .conformsTo("conformsTo")
                .creator("creator")
                .description(Set.of(CatalogMockObjectUtil.MULTILANGUAGE))
                .identifier(datasetId + "-identifier")
                .issued(OLDER_TIMESTAMP)
                .modified(NEWER_TIMESTAMP)
                .title(datasetId + "-title")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .distribution(new HashSet<>(Set.of(pullDistribution, staleDistribution)))
                .artifact(CatalogMockObjectUtil.ARTIFACT_FILE)
                .tenantId(TENANT_ID)
                .createdBy("creator")
                .lastModifiedBy("modifier")
                .version(3L)
                .build();
    }

    private boolean containsDistributionId(Iterable<Distribution> distributions, String distributionId) {
        return toDistributionList(distributions).stream()
                .map(Distribution::getId)
                .anyMatch(distributionId::equals);
    }

    private boolean containsString(Iterable<?> values, String expectedValue) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(String.valueOf(value)));
        result.sort(String::compareTo);
        return result.contains(expectedValue);
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

    private List<String> toStringList(Iterable<String> values) {
        List<String> result = new ArrayList<>();
        values.forEach(result::add);
        result.sort(String::compareTo);
        return result;
    }
}
