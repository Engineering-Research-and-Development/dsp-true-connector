package it.eng.datatransfer.event;

/**
 * Event published when the set of registered Data Planes changes.
 *
 * @param changeType  the kind of registration change
 * @param dataplaneId the registered Data Plane identifier
 * @param endpoint    the registered Data Plane endpoint
 */
public record DataPlaneRegistrationChangedEvent(ChangeType changeType, String dataplaneId, String endpoint) {

    /**
     * The supported Data Plane registration change types.
     */
    public enum ChangeType {
        REGISTERED,
        DEREGISTERED
    }
}
