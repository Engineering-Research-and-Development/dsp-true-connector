package it.eng.datatransfer.service;

import it.eng.tools.model.Tenant;
import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.TenantMetrics;
import it.eng.tools.model.dashboard.TransferSnapshotMetrics;
import it.eng.tools.repository.TenantRepository;
import org.bson.Document;
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
public class TransferMetricsService {

    private static final String COLLECTION_NAME = "transfer_process";
    private static final String STATE_FIELD = "state";
    private static final String ROLE_FIELD = "role";
    private static final String FORMAT_FIELD = "format";
    private static final String IS_DOWNLOADED_FIELD = "isDownloaded";
    private static final String IS_DOWNLOAD_IN_PROGRESS_FIELD = "isDownloadInProgress";
    private static final String TENANT_ID_FIELD = "tenantId";
    private static final String KEY_FIELD = "key";
    private static final String COUNT_FIELD = "count";
    private static final String COUNTS_BY_STATE_FIELD = "countsByState";
    private static final String COUNTS_BY_ROLE_AND_STATE_FIELD = "countsByRoleAndState";
    private static final String COUNTS_BY_FORMAT_FIELD = "countsByFormat";
    private static final String COUNTS_BY_DOWNLOAD_FLAG_FIELD = "countsByDownloadFlag";
    private static final String DOWNLOADED_COUNTS_FIELD = "downloadedCounts";
    private static final String DOWNLOAD_IN_PROGRESS_COUNTS_FIELD = "downloadInProgressCounts";
    private static final String TOTAL_FIELD = "total";
    private static final String DOWNLOADED_TRUE = "DOWNLOADED_TRUE";
    private static final String DOWNLOADED_FALSE = "DOWNLOADED_FALSE";
    private static final String DOWNLOAD_IN_PROGRESS_TRUE = "DOWNLOAD_IN_PROGRESS_TRUE";
    private static final String DOWNLOAD_IN_PROGRESS_FALSE = "DOWNLOAD_IN_PROGRESS_FALSE";
    private static final Comparator<KeyCount> KEY_COUNT_COMPARATOR = Comparator
            .comparingLong(KeyCount::count)
            .reversed()
            .thenComparing(KeyCount::key);

    private final MongoTemplate mongoTemplate;
    private final TenantRepository tenantRepository;

    public TransferMetricsService(MongoTemplate mongoTemplate, TenantRepository tenantRepository) {
        this.mongoTemplate = mongoTemplate;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Returns current transfer snapshot metrics for the requested tenant scope.
     *
     * @param tenantId the tenant identifier to filter on, or blank to aggregate across tenants
     * @return the aggregated snapshot metrics
     */
    public TransferSnapshotMetrics getSnapshotMetrics(String tenantId) {
        Criteria criteria = buildCriteria(tenantId);
        List<Document> aggregatedResults = mongoTemplate.aggregate(buildAggregation(criteria), COLLECTION_NAME, Document.class)
                .getMappedResults();
        Document snapshot = aggregatedResults.isEmpty() ? new Document() : aggregatedResults.get(0);
        long totalCount = getTotalCount(snapshot);
        long downloadedCount = getCountByKey(snapshot, COUNTS_BY_DOWNLOAD_FLAG_FIELD, DOWNLOADED_TRUE, tenantId);
        long downloadInProgressCount = getCountByKey(snapshot, COUNTS_BY_DOWNLOAD_FLAG_FIELD, DOWNLOAD_IN_PROGRESS_TRUE, tenantId);
        
        List<TenantMetrics<TransferSnapshotMetrics>> byTenant = StringUtils.hasText(tenantId) ? null : buildByTenant(snapshot);
        
        return new TransferSnapshotMetrics(
                totalCount,
                getCounts(snapshot, COUNTS_BY_STATE_FIELD, tenantId),
                getCounts(snapshot, COUNTS_BY_ROLE_AND_STATE_FIELD, tenantId),
                getCounts(snapshot, COUNTS_BY_FORMAT_FIELD, tenantId),
                downloadedCount,
                downloadInProgressCount,
                byTenant
        );
    }

    private Criteria buildCriteria(String tenantId) {
        Criteria criteria = new Criteria();
        if (StringUtils.hasText(tenantId)) {
            criteria.and(TENANT_ID_FIELD).is(tenantId.trim());
        }
        return criteria;
    }

    private Aggregation buildAggregation(Criteria criteria) {
        return Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.facet(
                        // State counts: group by state and tenantId
                        Aggregation.group(STATE_FIELD, TENANT_ID_FIELD).count().as(COUNT_FIELD),
                        Aggregation.project(COUNT_FIELD)
                                .andExpression("_id." + STATE_FIELD).as(KEY_FIELD)
                                .andExpression("_id." + TENANT_ID_FIELD).as(TENANT_ID_FIELD)
                                .andExclude("_id")
                ).as(COUNTS_BY_STATE_FIELD)
                        // Role+State counts: group by role, state, and tenantId
                        .and(
                                context -> new Document("$group", new Document("_id", new Document(ROLE_FIELD, "$" + ROLE_FIELD)
                                        .append(STATE_FIELD, "$" + STATE_FIELD)
                                        .append(TENANT_ID_FIELD, "$" + TENANT_ID_FIELD))
                                        .append(COUNT_FIELD, new Document("$sum", 1))),
                                context -> new Document("$project", new Document(KEY_FIELD, new Document("$concat", List.of(
                                        "$_id." + ROLE_FIELD,
                                        ":",
                                        "$_id." + STATE_FIELD
                                )))
                                        .append(COUNT_FIELD, "$" + COUNT_FIELD)
                                        .append(TENANT_ID_FIELD, "$_id." + TENANT_ID_FIELD)
                                        .append("_id", 0))
                        ).as(COUNTS_BY_ROLE_AND_STATE_FIELD)
                        // Format counts: group by format and tenantId
                        .and(
                                Aggregation.group(FORMAT_FIELD, TENANT_ID_FIELD).count().as(COUNT_FIELD),
                                Aggregation.project(COUNT_FIELD)
                                        .andExpression("_id." + FORMAT_FIELD).as(KEY_FIELD)
                                        .andExpression("_id." + TENANT_ID_FIELD).as(TENANT_ID_FIELD)
                                        .andExclude("_id")
                        ).as(COUNTS_BY_FORMAT_FIELD)
                        // Download flag counts: group by isDownloaded and tenantId
                        .and(
                                Aggregation.group(IS_DOWNLOADED_FIELD, TENANT_ID_FIELD).count().as(COUNT_FIELD),
                                context -> new Document("$project", new Document(KEY_FIELD, new Document("$cond", List.of(
                                        "$_id." + IS_DOWNLOADED_FIELD,
                                        DOWNLOADED_TRUE,
                                        DOWNLOADED_FALSE
                                )))
                                        .append(COUNT_FIELD, "$" + COUNT_FIELD)
                                        .append(TENANT_ID_FIELD, "$_id." + TENANT_ID_FIELD)
                                        .append("_id", 0))
                        ).as(DOWNLOADED_COUNTS_FIELD)
                        // Download in progress flag counts: group by isDownloadInProgress and tenantId
                        .and(
                                Aggregation.group(IS_DOWNLOAD_IN_PROGRESS_FIELD, TENANT_ID_FIELD).count().as(COUNT_FIELD),
                                context -> new Document("$project", new Document(KEY_FIELD, new Document("$cond", List.of(
                                        "$_id." + IS_DOWNLOAD_IN_PROGRESS_FIELD,
                                        DOWNLOAD_IN_PROGRESS_TRUE,
                                        DOWNLOAD_IN_PROGRESS_FALSE
                                )))
                                        .append(COUNT_FIELD, "$" + COUNT_FIELD)
                                        .append(TENANT_ID_FIELD, "$_id." + TENANT_ID_FIELD)
                                        .append("_id", 0))
                        ).as(DOWNLOAD_IN_PROGRESS_COUNTS_FIELD)
                        // Total: group by tenantId to get per-tenant counts
                        .and(
                                Aggregation.group(TENANT_ID_FIELD).count().as(COUNT_FIELD),
                                Aggregation.project(COUNT_FIELD).andExpression("_id").as(TENANT_ID_FIELD).andExclude("_id")
                        ).as(TOTAL_FIELD),
                context -> new Document("$project", new Document(COUNTS_BY_STATE_FIELD, 1)
                        .append(COUNTS_BY_ROLE_AND_STATE_FIELD, 1)
                        .append(COUNTS_BY_FORMAT_FIELD, 1)
                        .append(COUNTS_BY_DOWNLOAD_FLAG_FIELD, new Document("$concatArrays", List.of(
                                "$" + DOWNLOADED_COUNTS_FIELD,
                                "$" + DOWNLOAD_IN_PROGRESS_COUNTS_FIELD
                        )))
                        .append(TOTAL_FIELD, "$" + TOTAL_FIELD)
                        .append("_id", 0))
        );
    }

    private List<KeyCount> getCounts(Document snapshot, String fieldName, String tenantId) {
        Object value = snapshot.get(fieldName);
        if (!(value instanceof List<?> rawCounts)) {
            return List.of();
        }
        List<KeyCount> counts = rawCounts.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(this::toKeyCount)
                .flatMap(Optional::stream)
                .sorted(KEY_COUNT_COMPARATOR)
                .toList();
        
        // When tenantId is null (super-admin scope), aggregate counts by summing identical keys across tenants
        if (!StringUtils.hasText(tenantId)) {
            return aggregateCountsByKey(counts);
        }
        
        return counts;
    }

    /**
     * Aggregates KeyCount objects by summing counts for identical keys.
     * Used when retrieving metrics for super-admin scope (tenantId=null).
     *
     * @param counts the KeyCount objects to aggregate
     * @return aggregated list with summed counts for each unique key
     */
    private List<KeyCount> aggregateCountsByKey(List<KeyCount> counts) {
        return counts.stream()
                .collect(Collectors.groupingBy(
                        KeyCount::key,
                        Collectors.summingLong(KeyCount::count)
                ))
                .entrySet()
                .stream()
                .map(entry -> new KeyCount(entry.getKey(), entry.getValue()))
                .sorted(KEY_COUNT_COMPARATOR)
                .toList();
    }

    private Optional<KeyCount> toKeyCount(Document document) {
        Object key = document.get(KEY_FIELD);
        Object count = document.get(COUNT_FIELD);
        if (key == null || count == null) {
            return Optional.empty();
        }
        return Optional.of(new KeyCount(String.valueOf(key), extractLong(count)));
    }

    private long getTotalCount(Document snapshot) {
        List<TenantCount> tenantCounts = getTenantCounts(snapshot, TOTAL_FIELD);
        return tenantCounts.stream()
                .mapToLong(TenantCount::count)
                .sum();
    }

    private List<TenantCount> getTenantCounts(Document snapshot, String fieldName) {
        Object value = snapshot.get(fieldName);
        if (!(value instanceof List<?> rawCounts)) {
            return List.of();
        }
        return rawCounts.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(this::toTenantCount)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<TenantCount> toTenantCount(Document document) {
        Object tenantId = document.get(TENANT_ID_FIELD);
        Object count = document.get(COUNT_FIELD);
        if (tenantId == null || count == null) {
            return Optional.empty();
        }
        return Optional.of(new TenantCount(String.valueOf(tenantId), extractLong(count)));
    }

    private long getCountByKey(Document snapshot, String fieldName, String key, String tenantId) {
        return getCounts(snapshot, fieldName, tenantId).stream()
                .filter(kc -> kc.key().equals(key))
                .mapToLong(KeyCount::count)
                .sum();
    }

    private long extractLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private List<TenantMetrics<TransferSnapshotMetrics>> buildByTenant(Document snapshot) {
        List<Tenant> tenants = tenantRepository.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getId))
                .toList();

        List<TenantCount> tenantCounts = getTenantCounts(snapshot, TOTAL_FIELD);
        Map<String, Long> countsByTenant = tenantCounts.stream()
                .collect(Collectors.toMap(TenantCount::tenantId, TenantCount::count));

        return tenants.stream()
                .map(tenant -> {
                    String tenantId = tenant.getId();
                    long totalCount = countsByTenant.getOrDefault(tenantId, 0L);
                    List<KeyCount> byState = getCountsByTenant(snapshot, COUNTS_BY_STATE_FIELD, tenantId);
                    List<KeyCount> byRoleAndState = getCountsByTenant(snapshot, COUNTS_BY_ROLE_AND_STATE_FIELD, tenantId);
                    List<KeyCount> byFormat = getCountsByTenant(snapshot, COUNTS_BY_FORMAT_FIELD, tenantId);
                    long downloadedCount = getCountByKeyAndTenant(snapshot, COUNTS_BY_DOWNLOAD_FLAG_FIELD, DOWNLOADED_TRUE, tenantId);
                    long downloadInProgressCount = getCountByKeyAndTenant(snapshot, COUNTS_BY_DOWNLOAD_FLAG_FIELD, DOWNLOAD_IN_PROGRESS_TRUE, tenantId);

                    TransferSnapshotMetrics tenantMetrics = new TransferSnapshotMetrics(
                            totalCount,
                            byState,
                            byRoleAndState,
                            byFormat,
                            downloadedCount,
                            downloadInProgressCount,
                            null
                    );
                    return new TenantMetrics<>(tenantId, tenant.getName(), tenantMetrics);
                })
                .toList();
    }

    private List<KeyCount> getCountsByTenant(Document snapshot, String fieldName, String tenantId) {
        Object value = snapshot.get(fieldName);
        if (!(value instanceof List<?> rawCounts)) {
            return List.of();
        }
        return rawCounts.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .filter(doc -> tenantId.equals(String.valueOf(doc.get(TENANT_ID_FIELD))))
                .map(this::toKeyCount)
                .flatMap(Optional::stream)
                .sorted(KEY_COUNT_COMPARATOR)
                .toList();
    }

    private long getCountByKeyAndTenant(Document snapshot, String fieldName, String key, String tenantId) {
        return getCountsByTenant(snapshot, fieldName, tenantId).stream()
                .filter(kc -> kc.key().equals(key))
                .mapToLong(KeyCount::count)
                .sum();
    }

    private record TenantCount(String tenantId, long count) {
    }
}
