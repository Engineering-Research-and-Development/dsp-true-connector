package it.eng.connector.catalog;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reconciles dataset and catalog distributions against the transfer formats exposed by registered Data Planes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CatalogDataPlaneFormatSyncService {

    private final DataPlaneRegistrationService dataPlaneRegistrationService;
    private final DatasetRepository datasetRepository;
    private final DistributionRepository distributionRepository;
    private final CatalogRepository catalogRepository;

    /**
     * Resolves the supported transfer formats from all registered Data Planes.
     *
     * @return the union of advertised supported transfer types
     */
    public Set<String> resolveSupportedFormats() {
        return dataPlaneRegistrationService.findAll().stream()
                .map(DataPlaneRegistration::getSupportedTransferTypes)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Reconciles dataset distributions and refreshes catalog distribution references.
     */
    public void reconcileCatalogDistributions() {
        Set<String> supportedFormats = resolveSupportedFormats();
        if (supportedFormats.isEmpty()) {
            log.info("Skipping catalog/dataplane format reconciliation because no dataplane formats are registered");
            return;
        }

        List<Dataset> datasets = datasetRepository.findAll();
        Map<String, Distribution> reconciledDistributionsById = new LinkedHashMap<>();
        Set<String> staleDistributionIds = new LinkedHashSet<>();
        Map<String, Dataset> reconciledDatasetsById = new LinkedHashMap<>();

        for (Dataset dataset : datasets) {
            Dataset reconciledDataset = reconcileDataset(dataset, supportedFormats, reconciledDistributionsById, staleDistributionIds);
            reconciledDatasetsById.put(reconciledDataset.getId(), reconciledDataset);
        }

        if (!reconciledDistributionsById.isEmpty()) {
            distributionRepository.saveAll(reconciledDistributionsById.values());
        }
        if (!reconciledDatasetsById.isEmpty()) {
            datasetRepository.saveAll(reconciledDatasetsById.values());
        }

        List<Catalog> catalogs = catalogRepository.findAll();
        List<Catalog> reconciledCatalogs = catalogs.stream()
                .map(catalog -> reconcileCatalog(catalog, reconciledDatasetsById))
                .toList();
        if (!reconciledCatalogs.isEmpty()) {
            catalogRepository.saveAll(reconciledCatalogs);
        }

        Set<String> liveDistributionIds = reconciledDistributionsById.keySet();
        List<String> idsToDelete = staleDistributionIds.stream()
                .filter(id -> !liveDistributionIds.contains(id))
                .toList();
        if (!idsToDelete.isEmpty()) {
            distributionRepository.deleteAllById(idsToDelete);
        }

        log.info("Reconciled {} datasets, {} catalogs, {} active formats and {} stale distributions",
                reconciledDatasetsById.size(), reconciledCatalogs.size(), supportedFormats.size(), idsToDelete.size());
    }

    private Dataset reconcileDataset(Dataset dataset, Set<String> supportedFormats,
                                     Map<String, Distribution> reconciledDistributionsById,
                                     Set<String> staleDistributionIds) {
        Set<Distribution> currentDistributions = safeSet(dataset.getDistribution());
        Map<String, Distribution> distributionsByFormat = currentDistributions.stream()
                .filter(Objects::nonNull)
                .filter(distribution -> StringUtils.isNotBlank(distribution.getFormat()))
                .collect(Collectors.toMap(Distribution::getFormat, distribution -> distribution,
                        this::preferMostRecentDistribution, LinkedHashMap::new));
        Distribution template = currentDistributions.stream()
                .filter(Objects::nonNull)
                .max(this::compareByRecency)
                .orElse(null);

        Set<Distribution> reconciledDistributions = new LinkedHashSet<>();
        for (String supportedFormat : supportedFormats) {
            Distribution existingDistribution = distributionsByFormat.get(supportedFormat);
            Distribution templateSource = template != null ? template : existingDistribution;
            if (existingDistribution == null && templateSource == null) {
                continue;
            }
            Distribution distribution = materializeDistribution(
                    templateSource != null ? templateSource : existingDistribution,
                    supportedFormat,
                    existingDistribution);
            reconciledDistributions.add(distribution);
            reconciledDistributionsById.put(distribution.getId(), distribution);
        }

        Set<String> reconciledIds = reconciledDistributions.stream()
                .map(Distribution::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        currentDistributions.stream()
                .map(Distribution::getId)
                .filter(Objects::nonNull)
                .filter(id -> !reconciledIds.contains(id))
                .forEach(staleDistributionIds::add);

        return copyDataset(dataset, reconciledDistributions);
    }

    private Catalog reconcileCatalog(Catalog catalog, Map<String, Dataset> reconciledDatasetsById) {
        Set<Dataset> reconciledDatasets = safeSet(catalog.getDataset()).stream()
                .filter(Objects::nonNull)
                .map(dataset -> reconciledDatasetsById.getOrDefault(dataset.getId(), dataset))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Distribution> catalogDistributions = reconciledDatasets.stream()
                .map(Dataset::getDistribution)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return Catalog.Builder.newInstance()
                .id(catalog.getId())
                .keyword(catalog.getKeyword())
                .theme(catalog.getTheme())
                .conformsTo(catalog.getConformsTo())
                .creator(catalog.getCreator())
                .description(catalog.getDescription())
                .identifier(catalog.getIdentifier())
                .issued(catalog.getIssued())
                .modified(catalog.getModified())
                .title(catalog.getTitle())
                .distribution(catalogDistributions)
                .hasPolicy(catalog.getHasPolicy())
                .dataset(reconciledDatasets)
                .service(catalog.getService())
                .participantId(catalog.getParticipantId())
                .tenantId(catalog.getTenantId())
                .createdBy(catalog.getCreatedBy())
                .lastModifiedBy(catalog.getLastModifiedBy())
                .version(catalog.getVersion())
                .build();
    }

    private Dataset copyDataset(Dataset dataset, Set<Distribution> distributions) {
        return Dataset.Builder.newInstance()
                .id(dataset.getId())
                .keyword(dataset.getKeyword())
                .theme(dataset.getTheme())
                .conformsTo(dataset.getConformsTo())
                .creator(dataset.getCreator())
                .description(dataset.getDescription())
                .identifier(dataset.getIdentifier())
                .issued(dataset.getIssued())
                .modified(dataset.getModified())
                .title(dataset.getTitle())
                .hasPolicy(dataset.getHasPolicy())
                .distribution(distributions)
                .artifact(dataset.getArtifact())
                .tenantId(dataset.getTenantId())
                .createdBy(dataset.getCreatedBy())
                .lastModifiedBy(dataset.getLastModifiedBy())
                .version(dataset.getVersion())
                .build();
    }

    private Distribution materializeDistribution(Distribution template, String format, Distribution existingDistribution) {
        return Distribution.Builder.newInstance()
                .id(existingDistribution != null ? existingDistribution.getId() : null)
                .title(template.getTitle())
                .description(template.getDescription())
                .issued(template.getIssued())
                .modified(template.getModified())
                .hasPolicy(template.getHasPolicy())
                .format(format)
                .tenantId(existingDistribution != null ? existingDistribution.getTenantId() : template.getTenantId())
                .createdBy(existingDistribution != null ? existingDistribution.getCreatedBy() : template.getCreatedBy())
                .lastModifiedBy(existingDistribution != null ? existingDistribution.getLastModifiedBy() : template.getLastModifiedBy())
                .version(existingDistribution != null ? existingDistribution.getVersion() : null)
                .accessService(template.getAccessService())
                .build();
    }

    private Distribution preferMostRecentDistribution(Distribution left, Distribution right) {
        return compareByRecency(left, right) >= 0 ? left : right;
    }

    private int compareByRecency(Distribution left, Distribution right) {
        return recencyOf(left).compareTo(recencyOf(right));
    }

    private Instant recencyOf(Distribution distribution) {
        if (distribution == null) {
            return Instant.MIN;
        }
        if (distribution.getModified() != null) {
            return distribution.getModified();
        }
        if (distribution.getIssued() != null) {
            return distribution.getIssued();
        }
        return Instant.MIN;
    }

    private <T> Set<T> safeSet(Set<T> values) {
        return values != null ? values : Collections.emptySet();
    }
}
