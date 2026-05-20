package it.eng.negotiation.service;

import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.NegotiationSnapshotMetrics;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class NegotiationMetricsService {

    private static final String COLLECTION_NAME = "contract_negotiations";
    private static final String STATE_FIELD = "state";
    private static final String ROLE_FIELD = "role";
    private static final String TENANT_ID_FIELD = "tenantId";
    private static final String COUNT_FIELD = "count";
    private static final String KEY_FIELD = "key";
    private static final String TOTAL_FIELD = "total";

    private final MongoTemplate mongoTemplate;

    public NegotiationMetricsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Returns current negotiation snapshot metrics for the requested tenant scope.
     *
     * @param tenantId the tenant identifier to filter on, or blank to aggregate across tenants
     * @return the aggregated snapshot metrics
     */
    public NegotiationSnapshotMetrics getSnapshotMetrics(String tenantId) {
        Criteria criteria = buildCriteria(tenantId);
        List<KeyCount> countsByState = getCountsByState(criteria);
        List<KeyCount> countsByRoleAndState = getCountsByRoleAndState(criteria);
        long total = getTotalCount(criteria);
        return new NegotiationSnapshotMetrics(countsByState, countsByRoleAndState, total);
    }

    private Criteria buildCriteria(String tenantId) {
        Criteria criteria = new Criteria();
        if (StringUtils.hasText(tenantId)) {
            criteria.and(TENANT_ID_FIELD).is(tenantId.trim());
        }
        return criteria;
    }

    private List<KeyCount> getCountsByState(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group(STATE_FIELD).count().as(COUNT_FIELD),
                Aggregation.project(COUNT_FIELD).and("_id").as(KEY_FIELD),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, COUNT_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toKeyCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<KeyCount> getCountsByRoleAndState(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                context -> new Document("$group", new Document("_id", new Document(ROLE_FIELD, "$" + ROLE_FIELD)
                        .append(STATE_FIELD, "$" + STATE_FIELD))
                        .append(COUNT_FIELD, new Document("$sum", 1))),
                context -> new Document("$project", new Document(KEY_FIELD, new Document("$concat", List.of(
                        "$_id." + ROLE_FIELD,
                        ":",
                        "$_id." + STATE_FIELD
                )))
                        .append(COUNT_FIELD, 1)
                        .append("_id", 0)),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, COUNT_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toKeyCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private long getTotalCount(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().count().as(TOTAL_FIELD)
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Document.class);
        return results.getMappedResults()
                .stream()
                .findFirst()
                .map(document -> extractLong(document.get(TOTAL_FIELD)))
                .orElse(0L);
    }

    private KeyCount toKeyCount(Document document) {
        Object key = document.get(KEY_FIELD);
        if (key == null) {
            return null;
        }
        return new KeyCount(String.valueOf(key), extractLong(document.get(COUNT_FIELD)));
    }

    private long extractLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
