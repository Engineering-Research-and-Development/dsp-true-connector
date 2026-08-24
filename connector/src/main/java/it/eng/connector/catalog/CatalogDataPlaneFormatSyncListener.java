package it.eng.connector.catalog;

import it.eng.datatransfer.event.DataPlaneRegistrationChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reconciles catalog distributions when the registered Data Plane set changes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CatalogDataPlaneFormatSyncListener {

    private final CatalogDataPlaneFormatSyncService catalogDataPlaneFormatSyncService;

    /**
     * Refreshes dataset distributions after Data Plane registration changes.
     *
     * @param event the Data Plane registration change event
     */
    @EventListener
    public void handleRegistrationChange(DataPlaneRegistrationChangedEvent event) {
        log.info("Reconciling catalog distributions after dataplane {} event for {}",
                event.changeType(), event.dataplaneId());
        catalogDataPlaneFormatSyncService.reconcileCatalogDistributions();
    }
}
