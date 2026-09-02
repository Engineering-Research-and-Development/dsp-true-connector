package it.eng.dataplane.core.service;

import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.model.DataPlaneAuditEvent;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.repository.DataPlaneAuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing Data Plane audit events.
 * Provides methods to save lifecycle events and query them with optional filters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPlaneAuditEventService {

    private static final String FIELD_EVENT_TYPE = "eventType";
    private static final String FIELD_PROCESS_ID = "processId";
    private static final String FIELD_TRANSFER_TYPE = "transferType";

    private final DataPlaneAuditEventRepository repository;
    private final MongoTemplate mongoTemplate;
    private final DataPlaneProperties properties;

    /**
     * Saves an audit event for a data flow lifecycle transition.
     *
     * @param eventType    the type of audit event
     * @param processId    the transfer process ID (may be null for non-flow events)
     * @param transferType the transfer type, e.g. {@code HttpData-PULL}
     * @param description  human-readable description
     * @param details      optional extra context (may be null)
     */
    public void saveEvent(DataPlaneAuditEventType eventType,
                          String processId,
                          String transferType,
                          String description,
                          Map<String, String> details) {
        try {
            DataPlaneAuditEvent event = DataPlaneAuditEvent.Builder.newInstance()
                    .eventType(eventType)
                    .processId(processId)
                    .transferType(transferType)
                    .description(description)
                    .details(details)
                    .source(properties.getEndpoint())
                    .build();
            repository.save(event);
        } catch (Exception e) {
            log.error("Failed to persist DP audit event of type {}: {}", eventType, e.getMessage(), e);
        }
    }

    /**
     * Returns a paginated list of audit events, optionally filtered by the provided criteria.
     *
     * <p>Supported filter keys (matched case-sensitively from request params):
     * <ul>
     *   <li>{@code eventType} — exact match against the stored description value</li>
     *   <li>{@code processId} — exact match</li>
     *   <li>{@code transferType} — exact match</li>
     * </ul>
     *
     * @param filters  map of field name to filter value (may be empty)
     * @param pageable pagination and sorting
     * @return page of matching audit events
     */
    public Page<DataPlaneAuditEvent> getAuditEvents(Map<String, String> filters, Pageable pageable) {
        Query query = buildQuery(filters);

        long total = mongoTemplate.count(query, DataPlaneAuditEvent.class);

        query.with(pageable);
        List<DataPlaneAuditEvent> results = mongoTemplate.find(query, DataPlaneAuditEvent.class);

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Returns a single audit event by its MongoDB document ID.
     *
     * @param id document ID
     * @return optional audit event
     */
    public Optional<DataPlaneAuditEvent> getById(String id) {
        return repository.findById(id);
    }

    /**
     * Returns all supported Data Plane audit event types as name/description pairs.
     *
     * @return collection of event type descriptors
     */
    public Collection<Map<String, String>> getEventTypes() {
        return Arrays.stream(DataPlaneAuditEventType.values())
                .map(t -> Map.of("name", t.name(), "description", t.toString()))
                .toList();
    }

    /**
     * Builds a MongoDB {@link Query} from the provided filter map.
     * Unknown keys are silently ignored.
     *
     * @param filters filter map
     * @return query with applied criteria
     */
    private Query buildQuery(Map<String, String> filters) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (filters == null || filters.isEmpty()) {
            return new Query();
        }

        String eventType = filters.get(FIELD_EVENT_TYPE);
        if (eventType != null && !eventType.isBlank()) {
            criteriaList.add(Criteria.where(FIELD_EVENT_TYPE).is(eventType));
        }

        String processId = filters.get(FIELD_PROCESS_ID);
        if (processId != null && !processId.isBlank()) {
            criteriaList.add(Criteria.where(FIELD_PROCESS_ID).is(processId));
        }

        String transferType = filters.get(FIELD_TRANSFER_TYPE);
        if (transferType != null && !transferType.isBlank()) {
            criteriaList.add(Criteria.where(FIELD_TRANSFER_TYPE).is(transferType));
        }

        if (criteriaList.isEmpty()) {
            return new Query();
        }
        return new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
    }
}
