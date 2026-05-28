package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.service.TenantContextHolder;

@ExtendWith(MockitoExtension.class)
public class DistributionServiceTest {

    private static final String TENANT_ID = "engineering";

    @Mock
    private DistributionRepository repository;

    @Mock
    private CatalogService catalogService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private DistributionService distributionService;

    private Distribution distribution;
    private Distribution updatedDistribution = CatalogMockObjectUtil.DISTRIBUTION_FOR_UPDATE;

    @BeforeEach
    void setUp() {
        distribution = CatalogMockObjectUtil.createNewDistribution();
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Get distribution by id - success")
    void getDistributionById_success() {
        when(repository.findByIdAndTenantId(distribution.getId(), TENANT_ID)).thenReturn(Optional.of(distribution));

        Distribution result = distributionService.getDistributionById(distribution.getId());

        assertEquals(distribution.getId(), result.getId());
        verify(repository).findByIdAndTenantId(distribution.getId(), TENANT_ID);
    }

    @Test
    @DisplayName("Get distribution by id - not found")
    void getDistributionById_notFound() {
        when(repository.findByIdAndTenantId("1", TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> distributionService.getDistributionById("1"));

        verify(repository).findByIdAndTenantId("1", TENANT_ID);
    }

    @Test
    @DisplayName("Get all distributions")
    void getAllDistributions_success() {
        distributionService.getAllDistributions();
        verify(repository).findAllByTenantId(TENANT_ID);
    }

    @Test
    @DisplayName("Save distribution")
    void saveDistribution_success() {
        when(repository.save(any(Distribution.class))).thenReturn(distribution);

        Distribution result = distributionService.saveDistribution(distribution);

        assertEquals(distribution.getId(), result.getId());
        verify(repository).save(distribution);
        verify(catalogService).updateCatalogDistributionAfterSave(distribution);
        verify(applicationEventPublisher).publishEvent((Object) argThat(event -> hasReason(event, "distribution-saved")));
    }

    @Test
    @DisplayName("Delete distribution - success")
    void deleteDistribution_success() {
        when(repository.findByIdAndTenantId(distribution.getId(), TENANT_ID)).thenReturn(Optional.of(distribution));

        distributionService.deleteDistribution(distribution.getId());

        verify(repository).findByIdAndTenantId(distribution.getId(), TENANT_ID);
        verify(repository).deleteById(distribution.getId());
        verify(catalogService).updateCatalogDistributionAfterDelete(distribution);
        verify(applicationEventPublisher).publishEvent((Object) argThat(event -> hasReason(event, "distribution-deleted")));
    }

    @Test
    @DisplayName("Delete distribution - not found")
    void deleteDistribution_notFound() {
        when(repository.findByIdAndTenantId("1", TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> distributionService.deleteDistribution("1"));

        verify(repository).findByIdAndTenantId("1", TENANT_ID);
        verify(repository, never()).delete(any(Distribution.class));
        verify(catalogService, never()).updateCatalogDistributionAfterDelete(any(Distribution.class));
    }

    @Test
    @DisplayName("Update distribution - success")
    void updateDistribution_success() {
        when(repository.findByIdAndTenantId(distribution.getId(), TENANT_ID)).thenReturn(Optional.of(distribution));
        when(repository.save(any(Distribution.class))).thenReturn(distribution);

        Distribution result = distributionService.updateDistribution(distribution.getId(), updatedDistribution);

        assertEquals(distribution.getId(), result.getId());
        verify(repository).findByIdAndTenantId(distribution.getId(), TENANT_ID);
        verify(repository).save(any(Distribution.class));
        verify(applicationEventPublisher).publishEvent((Object) argThat(event -> hasReason(event, "distribution-updated")));
    }

    private boolean hasReason(Object target, String expectedReason) {
        try {
            Method fullReconcileAccessor = target.getClass().getMethod("fullReconcile");
            Method reasonAccessor = target.getClass().getMethod("reason");
            Object fullReconcile = fullReconcileAccessor.invoke(target);
            Object reason = reasonAccessor.invoke(target);
            return Boolean.TRUE.equals(fullReconcile) && expectedReason.equals(reason);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
