package it.eng.negotiation.service;

import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.NegotiationSnapshotMetrics;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NegotiationMetricsService {

    private static final String COLLECTION_NAME = "contract_negotiations";
    private static final String STATE_FIELD = "state";
    private static final String ROLE_FIELD = "role";
    private static final String TENANT_ID_FIELD = "tenantId";
    private static final String COUNT_FIELD = "count";
    private static final String KEY_FIELD = "key";
    private static final Comparator<KeyCount> KEY_COUNT_COMPARATOR = Comparator
            .comparingLong(KeyCount::count)
            .reversed()
            .thenComparing(KeyCount::key);

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
        List<GroupedNegotiationCount> groupedCounts = getGroupedCounts(criteria);
        List<KeyCount> countsByState = getCountsByState(groupedCounts);
        List<KeyCount> countsByRoleAndState = getCountsByRoleAndState(groupedCounts);
        long total = getTotalCount(groupedCounts);
        return new NegotiationSnapshotMetrics(countsByState, countsByRoleAndState, total);
    }

    private Criteria buildCriteria(String tenantId) {
        Criteria criteria = new Criteria();
        if (StringUtils.hasText(tenantId)) {
            criteria.and(TENANT_ID_FIELD).is(tenantId.trim());
        }
        return criteria;
    }

    private List<GroupedNegotiationCount> getGroupedCounts(Criteria criteria) {
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
                        .append(STATE_FIELD, "$_id." + STATE_FIELD)
                        .append(COUNT_FIELD, "$" + COUNT_FIELD)
                        .append("_id", 0)),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, COUNT_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toGroupedCount)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<KeyCount> getCountsByState(List<GroupedNegotiationCount> groupedCounts) {
        Map<String, Long> countsByState = groupedCounts.stream()
                .collect(Collectors.groupingBy(
                        GroupedNegotiationCount::state,
                        Collectors.summingLong(GroupedNegotiationCount::count)
                ));

        return countsByState.entrySet()
                .stream()
                .map(entry -> new KeyCount(entry.getKey(), entry.getValue()))
                .sorted(KEY_COUNT_COMPARATOR)
                .toList();
    }

    private List<KeyCount> getCountsByRoleAndState(List<GroupedNegotiationCount> groupedCounts) {
        return groupedCounts.stream()
                .map(groupedCount -> new KeyCount(groupedCount.key(), groupedCount.count()))
                .sorted(KEY_COUNT_COMPARATOR)
                .toList();
    }

    private long getTotalCount(List<GroupedNegotiationCount> groupedCounts) {
        return groupedCounts.stream()
                .mapToLong(GroupedNegotiationCount::count)
                .sum();
    }

    private Optional<GroupedNegotiationCount> toGroupedCount(Document document) {
        Object key = document.get(KEY_FIELD);
        Object state = document.get(STATE_FIELD);
        if (key == null || state == null) {
            return Optional.empty();
        }
        return Optional.of(new GroupedNegotiationCount(
                String.valueOf(state),
                String.valueOf(key),
                extractLong(document.get(COUNT_FIELD))
        ));
    }

    private long extractLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private record GroupedNegotiationCount(String state, String key, long count) {
    }
}
