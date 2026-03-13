package it.eng.dcp.common.audit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the DCP audit event subsystem.
 *
 * <p>Bind under the {@code dcp.audit} prefix in {@code application.yml}:
 * <pre>
 * dcp:
 *   audit:
 *     enabled: true
 *     collection-name: dcp_audit_events
 * </pre>
 *
 * <p>To merge DCP events with connector events in a shared MongoDB collection
 * (so that a frontend can display a unified timeline) set
 * {@code collection-name: audit_events}.
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "dcp.audit")
public class DcpAuditProperties {

    /**
     * Whether audit event persistence is active.
     * Defaults to {@code true}. Set to {@code false} to disable all audit writes.
     */
    private boolean enabled = true;

    /**
     * Name of the MongoDB collection that audit events are written to.
     * Defaults to {@code "dcp_audit_events"}.
     * Set to {@code "audit_events"} to share the collection with the connector.
     */
    private String collectionName = "dcp_audit_events";
}
