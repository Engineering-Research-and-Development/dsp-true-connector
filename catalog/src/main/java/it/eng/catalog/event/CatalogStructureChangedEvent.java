package it.eng.catalog.event;

/**
 * Event published when catalog-visible dataset or distribution structure changes.
 *
 * @param scope  the reconcile scope requested by the event
 * @param reason the reason that triggered the reconcile request
 */
public record CatalogStructureChangedEvent(Scope scope, String reason) {

    /**
     * The supported catalog reconcile scopes.
     */
    public enum Scope {
        FULL_RECONCILE
    }

    /**
     * Creates an event requesting a full catalog reconcile.
     *
     * @param reason the reason that triggered the reconcile request
     * @return the full-reconcile event
     */
    public static CatalogStructureChangedEvent fullReconcile(String reason) {
        return new CatalogStructureChangedEvent(Scope.FULL_RECONCILE, reason);
    }
}
