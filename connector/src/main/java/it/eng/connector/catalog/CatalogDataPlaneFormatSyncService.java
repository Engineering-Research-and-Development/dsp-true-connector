package it.eng.connector.catalog;

import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
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

        Set<String> liveDistributionIds = reconciledDistributionsById.keySet();
        List<String> idsToDelete = staleDistributionIds.stream()
                .filter(id -> !liveDistributionIds.contains(id))
                .toList();
        if (!idsToDelete.isEmpty()) {
            distributionRepository.deleteAllById(idsToDelete);
        }

        log.info("Reconciled {} datasets, {} active formats and {} stale distributions",
                reconciledDatasetsById.size(), supportedFormats.size(), idsToDelete.size());
    }

    private Dataset reconcileDataset(Dataset dataset, Set<String> supportedFormats,
                                     Map<String, Distribution> reconciledDistributionsById,
                                     Set<String> staleDistributionIds) {
        Set<Distribution> currentDistributions = safeSet(dataset.getDistribution());
        if (supportedFormats.isEmpty()) {
            return normalizeDatasetToTemplateDistribution(dataset, currentDistributions,
                    reconciledDistributionsById, staleDistributionIds);
        }
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

    private Dataset normalizeDatasetToTemplateDistribution(Dataset dataset, Set<Distribution> currentDistributions,
                                                           Map<String, Distribution> reconciledDistributionsById,
                                                           Set<String> staleDistributionIds) {
        Distribution template = currentDistributions.stream()
                .filter(Objects::nonNull)
                .max(this::compareByRecency)
                .orElse(null);
        if (template == null) {
            return dataset;
        }

        Distribution normalizedDistribution = materializeDistribution(template, null, template);
        reconciledDistributionsById.put(normalizedDistribution.getId(), normalizedDistribution);
        currentDistributions.stream()
                .map(Distribution::getId)
                .filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, normalizedDistribution.getId()))
                .forEach(staleDistributionIds::add);
        return copyDataset(dataset, new LinkedHashSet<>(Set.of(normalizedDistribution)));
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
