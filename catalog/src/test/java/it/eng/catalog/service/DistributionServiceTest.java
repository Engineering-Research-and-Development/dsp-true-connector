package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;

@ExtendWith(MockitoExtension.class)
public class DistributionServiceTest {

    @Mock
    private DistributionRepository repository;

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private DistributionService distributionService;

    private Distribution distribution = CatalogMockObjectUtil.DISTRIBUTION;
    private Distribution updatedDistribution = CatalogMockObjectUtil.DISTRIBUTION_FOR_UPDATE;

    @BeforeEach
    void setUp() {
        distribution = CatalogMockObjectUtil.DISTRIBUTION;
    }

    @Test
    @DisplayName("Get distribution by id - success")
    void getDistributionById_success() {
        when(repository.findById(distribution.getId())).thenReturn(Optional.of(distribution));

        Distribution result = distributionService.getDistributionById(distribution.getId());

        assertEquals(distribution.getId(), result.getId());
        verify(repository).findById(distribution.getId());
    }

    @Test
    @DisplayName("Get distribution by id - not found")
    void getDistributionById_notFound() {
        when(repository.findById("1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> distributionService.getDistributionById("1"));

        verify(repository).findById("1");
    }

    @Test
    @DisplayName("Get all distributions")
    void getAllDistributions_success() {
        distributionService.getAllDistributions();
        verify(repository).findAll();
    }

    @Test
    @DisplayName("Save distribution")
    void saveDistribution_success() {
        when(repository.save(any(Distribution.class))).thenReturn(distribution);

        Distribution result = distributionService.saveDistribution(distribution);

        assertEquals(distribution.getId(), result.getId());
        verify(repository).save(distribution);
        verify(catalogService).updateCatalogDistributionAfterSave(distribution);
    }

    @Test
    @DisplayName("Delete distribution - success")
    void deleteDistribution_success() {
        when(repository.findById(distribution.getId())).thenReturn(Optional.of(distribution));

        distributionService.deleteDistribution(distribution.getId());

        verify(repository).findById(distribution.getId());
        verify(repository).deleteById(distribution.getId());
        verify(catalogService).updateCatalogDistributionAfterDelete(distribution);
    }

    @Test
    @DisplayName("Delete distribution - not found")
    void deleteDistribution_notFound() {
        when(repository.findById("1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> distributionService.deleteDistribution("1"));

        verify(repository).findById("1");
        verify(repository, never()).delete(any(Distribution.class));
        verify(catalogService, never()).updateCatalogDistributionAfterDelete(any(Distribution.class));
    }

    @Test
    @DisplayName("Update distribution - success")
    void updateDistribution_success() {
        when(repository.findById(distribution.getId())).thenReturn(Optional.of(distribution));
        when(repository.save(any(Distribution.class))).thenReturn(distribution);

        Distribution result = distributionService.updateDistribution(distribution.getId(), updatedDistribution);

        assertEquals(distribution.getId(), result.getId());
        verify(repository).findById(distribution.getId());
        verify(repository).save(any(Distribution.class));
    }
}
