package it.eng.catalog.event;

/**
 * Event published when catalog-visible dataset or distribution structure changes.
 *
 * @param reason        the reason that triggered the reconcile request
 * @param fullReconcile whether a full catalog reconcile is requested
 */
public record CatalogStructureChangedEvent(String reason, boolean fullReconcile) {

    /**
     * Creates an event requesting a full catalog reconcile.
     *
     * @param reason the reason that triggered the reconcile request
     * @return the full-reconcile event
     */
    public static CatalogStructureChangedEvent fullReconcile(String reason) {
        return new CatalogStructureChangedEvent(reason, true);
    }
}
