package it.eng.connector.integration.internal;

import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.repository.ArtifactRepository;

/**
 * Test data for the {@code /internal/**} chain integration tests.
 *
 * @param datasetRepository      dataset repository
 * @param distributionRepository distribution repository
 * @param dataServiceRepository  data-service repository
 * @param artifactRepository     artifact repository
 */
public record InternalChainFixtures(DatasetRepository datasetRepository,
                                    DistributionRepository distributionRepository,
                                    DataServiceRepository dataServiceRepository,
                                    ArtifactRepository artifactRepository) {

    /**
     * Stores a dataset of the given tenant with one distribution, its data service and an artifact.
     *
     * @param tenantId the dataset's tenant
     * @return the stored dataset
     */
    public Dataset persistDataset(String tenantId) {
        Dataset dataset = CatalogMockObjectUtil.createNewDataset(tenantId);
        dataset.getDistribution().forEach(distribution -> {
            dataServiceRepository.save(distribution.getAccessService());
            distributionRepository.save(distribution);
        });
        artifactRepository.save(dataset.getArtifact());
        return datasetRepository.save(dataset);
    }

    /**
     * Removes every catalog document the fixtures may have stored.
     */
    public void clear() {
        datasetRepository.deleteAll();
        distributionRepository.deleteAll();
        dataServiceRepository.deleteAll();
        artifactRepository.deleteAll();
    }

    /**
     * Returns the internal formats path of a dataset.
     *
     * @param datasetId the dataset ID
     * @return {@code /internal/v1/datasets/{id}/formats}
     */
    public static String formatsPath(String datasetId) {
        return ApiEndpoints.INTERNAL_DATASETS_V1 + "/" + datasetId + "/formats";
    }
}
