package it.eng.dcp.common.service.audit;

import it.eng.dcp.common.audit.DcpAuditEvent;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.audit.DcpAuditProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service that wraps Spring's {@link ApplicationEventPublisher} to publish
 * {@link DcpAuditEvent} instances.
 *
 * <p>Callers provide all context explicitly (source module, holder DID, issuer DID,
 * etc.) because the DCP modules do not maintain a per-request context service
 * analogous to the connector's {@code RequestInfoService}.
 *
 * <p>Publishing is skipped entirely when {@code dcp.audit.enabled=false}, so no
 * events reach the listener and no MongoDB writes occur.
 *
 * <p>Usage example:
 * <pre>{@code
 * auditPublisher.publishEvent(
 *     DcpAuditEventType.CREDENTIAL_SAVED,
 *     "MembershipCredential saved for holder",
 *     "holder",
 *     holderDid,
 *     issuerDid,
 *     List.of("MembershipCredential"),
 *     req.getIssuerPid(),
 *     Map.of("format", "jwt")
 * );
 * }</pre>
 */
@Service
@Slf4j
public class DcpAuditEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final DcpAuditProperties auditProperties;

    public DcpAuditEventPublisher(ApplicationEventPublisher applicationEventPublisher,
                                  DcpAuditProperties auditProperties) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.auditProperties = auditProperties;
    }

    /**
     * Constructs and publishes a {@link DcpAuditEvent} with the supplied parameters.
     *
     * @param eventType       the DCP event type (must not be {@code null})
     * @param description     human-readable description of what happened
     * @param source          originating module: {@code "issuer"}, {@code "holder"}, or {@code "verifier"}
     * @param holderDid       DID of the holder involved (may be {@code null})
     * @param issuerDid       DID of the issuer involved (may be {@code null})
     * @param credentialTypes credential type IDs involved (may be {@code null})
     * @param requestId       correlation ID mapping to issuerPid / holderPid (may be {@code null})
     * @param details         additional key/value data (may be {@code null})
     */
    public void publishEvent(DcpAuditEventType eventType,
                             String description,
                             String source,
                             String holderDid,
                             String issuerDid,
                             List<String> credentialTypes,
                             String requestId,
                             Map<String, Object> details) {
        if (!auditProperties.isEnabled()) {
            log.trace("DCP audit is disabled — skipping event type={}", eventType);
            return;
        }
        try {
            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(eventType)
                    .description(description)
                    .source(source)
                    .holderDid(holderDid)
                    .issuerDid(issuerDid)
                    .credentialTypes(credentialTypes)
                    .requestId(requestId)
                    .details(details)
                    .build();
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish DCP audit event type={}: {}", eventType, e.getMessage(), e);
        }
    }

    /**
     * Publishes an already-constructed {@link DcpAuditEvent} directly.
     *
     * @param event the audit event to publish
     */
    public void publishEvent(DcpAuditEvent event) {
        if (!auditProperties.isEnabled()) {
            log.trace("DCP audit is disabled — skipping event type={}", event.getEventType());
            return;
        }
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish DCP audit event type={}: {}", event.getEventType(), e.getMessage(), e);
        }
    }
}

