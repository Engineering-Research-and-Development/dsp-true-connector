package it.eng.tools.service;


import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.RequestInfo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final RequestInfoService requestInfoService;

    public AuditEventPublisher(ApplicationEventPublisher applicationEventPublisher, RequestInfoService requestInfoService) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.requestInfoService = requestInfoService;
    }

    /**
     * Publishes an audit event with the given type, description, and details.
     *
     * @param eventType   The type of the audit event, must match an AuditEventType enum value.
     * @param description The description of the audit event.
     * @param details     The details of the audit event, can be null or empty.
     */
    public void publishEvent(AuditEventType eventType, String description, Map<String, Object> details) {
        // Retrieve the current RequestInfo object
        RequestInfo requestInfo = requestInfoService.getCurrentRequestInfo();

        // Create an AuditEvent.Builder with request information
        AuditEvent.Builder auditEventBuilder = createAuditEventBuilderWithRequestInfo(requestInfo, description, eventType, details);

        // Build the AuditEvent
        AuditEvent auditEvent = auditEventBuilder.build();

        // Publish the event
        applicationEventPublisher.publishEvent(auditEvent);
    }

    public void publishEvent(AuditEvent auditEvent) {
        applicationEventPublisher.publishEvent(auditEvent);
    }

    public void publishEvent(Object event) {
        // Publish the event directly without additional processing
        applicationEventPublisher.publishEvent(event);
    }


    /**
     * Helper method to create an audit event builder with request information.
     *
     * @param requestInfo The RequestInfo object containing request details
     * @param description The description of the audit event
     * @param eventType   The type of the audit event
     * @param details     The details of the audit event
     * @return An AuditEvent.Builder with request information added if available
     */
    private AuditEvent.Builder createAuditEventBuilderWithRequestInfo(RequestInfo requestInfo, String description,
                                                                      AuditEventType eventType, Map<String, Object> details) {
        // Create the audit event builder
        AuditEvent.Builder auditEventBuilder = AuditEvent.Builder.newInstance()
                .description(description)
                .eventType(eventType)
                .details(details);

        // Add request information if available
        if (requestInfo != null) {
            auditEventBuilder.ipAddress(requestInfo.getRemoteAddress())
                    .username(requestInfo.getUsername())
                    .source(requestInfo.getRemoteHost());
        }
        return auditEventBuilder;
    }
}
