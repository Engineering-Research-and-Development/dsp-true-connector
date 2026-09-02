package it.eng.tools.service;

import it.eng.tools.model.Tenant;
import it.eng.tools.model.dashboard.HistoricalEventMetrics;
import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.TenantMetrics;
import it.eng.tools.model.dashboard.TimeBucketCount;
import it.eng.tools.model.dashboard.TimeWindow;
import it.eng.tools.repository.TenantRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AuditEventMetricsService {

    private static final String AUDIT_EVENTS_COLLECTION = "audit_events";
    private static final String KEY_FIELD = "key";
    private static final String COUNT_FIELD = "count";
    private static final String TOTAL_FIELD = "total";
    private static final String BUCKET_START_FIELD = "bucketStart";
    private static final String ROLE_FIELD = "details.role";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String EVENT_TYPE_FIELD = "eventType";
    private static final String TENANT_ID_FIELD = "tenantId";
    private static final String HOUR_BUCKET = "hour";
    private static final String DAY_BUCKET = "day";

    private final MongoTemplate mongoTemplate;
    private final TenantRepository tenantRepository;

    public AuditEventMetricsService(MongoTemplate mongoTemplate, TenantRepository tenantRepository) {
        this.mongoTemplate = mongoTemplate;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Returns historical audit event metrics for the requested time window and tenant scope.
     *
     * @param window the time window and bucket size to aggregate
     * @param tenantId the tenant identifier to filter on, or blank to aggregate across tenants
     * @return the aggregated historical metrics
     */
    public HistoricalEventMetrics getHistoricalMetrics(TimeWindow window, String tenantId) {
        Criteria criteria = buildCriteria(window, tenantId);
        List<TenantKeyCount> byEventTypeRows = getKeyCountsByFieldWithTenant(criteria, EVENT_TYPE_FIELD);
        List<TenantKeyCount> byRoleRows = getRoleCountsWithTenant(criteria);
        List<TenantTimeBucketCount> overTimeRows = getCountsOverTimeWithTenant(criteria, window.bucket());
        List<TenantCount> totalRows = getTotalCountWithTenant(criteria);
        
        long total = totalRows.stream().mapToLong(TenantCount::count).sum();
        List<KeyCount> countsByEventType = summarizeByKey(byEventTypeRows);
        List<KeyCount> countsByRole = summarizeByKey(byRoleRows);
        List<TimeBucketCount> countsOverTime = summarizeOverTime(overTimeRows);
        List<TenantMetrics<HistoricalEventMetrics>> byTenant = buildByTenant(tenantId, byEventTypeRows, byRoleRows, overTimeRows, totalRows);
        
        return new HistoricalEventMetrics(total, countsByEventType, countsByRole, countsOverTime, byTenant);
    }

    private Criteria buildCriteria(TimeWindow window, String tenantId) {
        Criteria criteria = Criteria.where(TIMESTAMP_FIELD)
                .gte(toLocalDateTime(window.from()))
                .lte(toLocalDateTime(window.to()));
        if (StringUtils.hasText(tenantId)) {
            criteria.and(TENANT_ID_FIELD).is(tenantId.trim());
        }
        return criteria;
    }

    private List<KeyCount> getKeyCountsByField(Criteria criteria, String field) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group(field).count().as(COUNT_FIELD),
                Aggregation.project(COUNT_FIELD).and("_id").as(KEY_FIELD),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, COUNT_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, AUDIT_EVENTS_COLLECTION, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toKeyCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<TenantKeyCount> getKeyCountsByFieldWithTenant(Criteria criteria, String field) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group(field, TENANT_ID_FIELD).count().as(COUNT_FIELD),
                Aggregation.project(COUNT_FIELD)
                        .andExpression("_id." + field).as(KEY_FIELD)
                        .andExpression("_id." + TENANT_ID_FIELD).as(TENANT_ID_FIELD),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, COUNT_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, AUDIT_EVENTS_COLLECTION, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toTenantKeyCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<KeyCount> getRoleCounts(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.match(new Criteria().andOperator(
                        Criteria.where(ROLE_FIELD).exists(true),
                        Criteria.where(ROLE_FIELD).ne(null),
                        Criteria.where(ROLE_FIELD).ne("")
                )),
                Aggregation.group(ROLE_FIELD).count().as(COUNT_FIELD),
                Aggregation.project(COUNT_FIELD).and("_id").as(KEY_FIELD),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, COUNT_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, AUDIT_EVENTS_COLLECTION, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toKeyCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<TenantKeyCount> getRoleCountsWithTenant(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.match(new Criteria().andOperator(
                        Criteria.where(ROLE_FIELD).exists(true),
                        Criteria.where(ROLE_FIELD).ne(null),
                        Criteria.where(ROLE_FIELD).ne("")
                )),
                Aggregation.group(ROLE_FIELD, TENANT_ID_FIELD).count().as(COUNT_FIELD),
                Aggregation.project(COUNT_FIELD)
                        .andExpression("_id." + ROLE_FIELD).as(KEY_FIELD)
                        .andExpression("_id." + TENANT_ID_FIELD).as(TENANT_ID_FIELD),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, COUNT_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, AUDIT_EVENTS_COLLECTION, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toTenantKeyCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<TimeBucketCount> getCountsOverTime(Criteria criteria, String bucket) {
        String bucketUnit = normalizeBucket(bucket);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                context -> new Document("$project", new Document(EVENT_TYPE_FIELD, 1)
                        .append(BUCKET_START_FIELD, new Document("$dateTrunc", new Document("date", "$" + TIMESTAMP_FIELD)
                                .append("unit", bucketUnit)
                                .append("timezone", "UTC")))),
                context -> new Document("$group", new Document("_id", new Document(BUCKET_START_FIELD, "$" + BUCKET_START_FIELD)
                        .append(KEY_FIELD, "$" + EVENT_TYPE_FIELD))
                        .append(COUNT_FIELD, new Document("$sum", 1))),
                context -> new Document("$project", new Document(BUCKET_START_FIELD, "$_id." + BUCKET_START_FIELD)
                        .append(KEY_FIELD, "$_id." + KEY_FIELD)
                        .append(COUNT_FIELD, 1)
                        .append("_id", 0)),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, BUCKET_START_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, AUDIT_EVENTS_COLLECTION, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toTimeBucketCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<TenantTimeBucketCount> getCountsOverTimeWithTenant(Criteria criteria, String bucket) {
        String bucketUnit = normalizeBucket(bucket);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                context -> new Document("$project", new Document(EVENT_TYPE_FIELD, 1)
                        .append(TENANT_ID_FIELD, 1)
                        .append(BUCKET_START_FIELD, new Document("$dateTrunc", new Document("date", "$" + TIMESTAMP_FIELD)
                                .append("unit", bucketUnit)
                                .append("timezone", "UTC")))),
                context -> new Document("$group", new Document("_id", new Document(BUCKET_START_FIELD, "$" + BUCKET_START_FIELD)
                        .append(KEY_FIELD, "$" + EVENT_TYPE_FIELD)
                        .append(TENANT_ID_FIELD, "$" + TENANT_ID_FIELD))
                        .append(COUNT_FIELD, new Document("$sum", 1))),
                context -> new Document("$project", new Document(BUCKET_START_FIELD, "$_id." + BUCKET_START_FIELD)
                        .append(KEY_FIELD, "$_id." + KEY_FIELD)
                        .append(TENANT_ID_FIELD, "$_id." + TENANT_ID_FIELD)
                        .append(COUNT_FIELD, 1)
                        .append("_id", 0)),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, BUCKET_START_FIELD).and(Sort.by(Sort.Direction.ASC, KEY_FIELD)))
        );

        return mongoTemplate.aggregate(aggregation, AUDIT_EVENTS_COLLECTION, Document.class)
                .getMappedResults()
                .stream()
                .map(this::toTenantTimeBucketCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private long getTotalCount(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().count().as(TOTAL_FIELD)
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, AUDIT_EVENTS_COLLECTION, Document.class);
        return results.getMappedResults()
                .stream()
                .findFirst()
                .map(document -> extractLong(document.get(TOTAL_FIELD)))
                .orElse(0L);
    }

    private List<TenantCount> getTotalCountWithTenant(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group(TENANT_ID_FIELD).count().as(TOTAL_FIELD),
                Aggregation.project(TOTAL_FIELD).andExpression("_id").as(TENANT_ID_FIELD)
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, AUDIT_EVENTS_COLLECTION, Document.class);
        return results.getMappedResults()
                .stream()
                .map(this::toTenantCount)
                .filter(Objects::nonNull)
                .toList();
    }

    private KeyCount toKeyCount(Document document) {
        Object key = document.get(KEY_FIELD);
        if (key == null) {
            return null;
        }
        return new KeyCount(String.valueOf(key), extractLong(document.get(COUNT_FIELD)));
    }

    private TenantKeyCount toTenantKeyCount(Document document) {
        Object key = document.get(KEY_FIELD);
        if (key == null) {
            return null;
        }
        return new TenantKeyCount(document.getString(TENANT_ID_FIELD), String.valueOf(key), extractLong(document.get(COUNT_FIELD)));
    }

    private TenantCount toTenantCount(Document document) {
        Object count = document.get(TOTAL_FIELD);
        if (count == null) {
            return null;
        }
        return new TenantCount(document.getString(TENANT_ID_FIELD), extractLong(count));
    }

    private TimeBucketCount toTimeBucketCount(Document document) {
        Object bucketStart = document.get(BUCKET_START_FIELD);
        Object key = document.get(KEY_FIELD);
        if (bucketStart == null || key == null) {
            return null;
        }
        return new TimeBucketCount(toInstant(bucketStart), String.valueOf(key), extractLong(document.get(COUNT_FIELD)));
    }

    private TenantTimeBucketCount toTenantTimeBucketCount(Document document) {
        Object bucketStart = document.get(BUCKET_START_FIELD);
        Object key = document.get(KEY_FIELD);
        if (bucketStart == null || key == null) {
            return null;
        }
        return new TenantTimeBucketCount(document.getString(TENANT_ID_FIELD), toInstant(bucketStart), String.valueOf(key), extractLong(document.get(COUNT_FIELD)));
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String normalizeBucket(String bucket) {
        if (HOUR_BUCKET.equalsIgnoreCase(bucket)) {
            return HOUR_BUCKET;
        }
        if (DAY_BUCKET.equalsIgnoreCase(bucket)) {
            return DAY_BUCKET;
        }
        throw new IllegalArgumentException("Unsupported time bucket: " + bucket);
    }

    private Instant toInstant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        return Instant.parse(String.valueOf(value));
    }

    private long extractLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static List<KeyCount> summarizeByKey(List<TenantKeyCount> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(TenantKeyCount::key, Collectors.summingLong(TenantKeyCount::count)))
                .entrySet().stream()
                .map(e -> new KeyCount(e.getKey(), e.getValue()))
                .toList();
    }

    private static List<TimeBucketCount> summarizeOverTime(List<TenantTimeBucketCount> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(TenantTimeBucketCount::bucketStart,
                        Collectors.groupingBy(TenantTimeBucketCount::key, Collectors.summingLong(TenantTimeBucketCount::count))))
                .entrySet().stream()
                .flatMap(bucketEntry -> bucketEntry.getValue().entrySet().stream()
                        .map(keyEntry -> new TimeBucketCount(bucketEntry.getKey(), keyEntry.getKey(), keyEntry.getValue())))
                .sorted(Comparator.comparing(TimeBucketCount::bucketStart).thenComparing(TimeBucketCount::key))
                .toList();
    }

    private List<TenantMetrics<HistoricalEventMetrics>> buildByTenant(
            String tenantId,
            List<TenantKeyCount> byEventTypeRows,
            List<TenantKeyCount> byRoleRows,
            List<TenantTimeBucketCount> overTimeRows,
            List<TenantCount> totalRows) {
        if (StringUtils.hasText(tenantId)) {
            return null;
        }

        return tenantRepository.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getId))
                .map(tenant -> {
                    String id = tenant.getId();
                    HistoricalEventMetrics tenantMetrics = new HistoricalEventMetrics(
                            totalRows.stream()
                                    .filter(r -> id.equals(r.tenantId()))
                                    .mapToLong(TenantCount::count)
                                    .sum(),
                            summarizeByKey(byEventTypeRows.stream()
                                    .filter(r -> id.equals(r.tenantId()))
                                    .toList()),
                            summarizeByKey(byRoleRows.stream()
                                    .filter(r -> id.equals(r.tenantId()))
                                    .toList()),
                            summarizeOverTime(overTimeRows.stream()
                                    .filter(r -> id.equals(r.tenantId()))
                                    .toList()),
                            null);
                    return new TenantMetrics<>(id, tenant.getName(), tenantMetrics);
                })
                .toList();
    }

    private record TenantKeyCount(String tenantId, String key, long count) {
    }

    private record TenantTimeBucketCount(String tenantId, Instant bucketStart, String key, long count) {
    }

    private record TenantCount(String tenantId, long count) {
    }
}
