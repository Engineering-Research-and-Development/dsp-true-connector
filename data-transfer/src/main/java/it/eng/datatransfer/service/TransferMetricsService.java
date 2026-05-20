package it.eng.datatransfer.service;

import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.TransferSnapshotMetrics;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

    public TransferMetricsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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
        Document snapshot = aggregatedResults.get(0);
        return new TransferSnapshotMetrics(
                getCounts(snapshot, COUNTS_BY_STATE_FIELD),
                getCounts(snapshot, COUNTS_BY_ROLE_AND_STATE_FIELD),
                getCounts(snapshot, COUNTS_BY_FORMAT_FIELD),
                getCounts(snapshot, COUNTS_BY_DOWNLOAD_FLAG_FIELD),
                getTotalCount(snapshot)
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
                        Aggregation.group(STATE_FIELD).count().as(COUNT_FIELD),
                        Aggregation.project(COUNT_FIELD).andExpression("_id").as(KEY_FIELD).andExclude("_id")
                ).as(COUNTS_BY_STATE_FIELD)
                        .and(
                                context -> new Document("$group", new Document("_id", new Document(ROLE_FIELD, "$" + ROLE_FIELD)
                                        .append(STATE_FIELD, "$" + STATE_FIELD))
                                        .append(COUNT_FIELD, new Document("$sum", 1))),
                                context -> new Document("$project", new Document(KEY_FIELD, new Document("$concat", List.of(
                                        "$_id." + ROLE_FIELD,
                                        ":",
                                        "$_id." + STATE_FIELD
                                )))
                                        .append(COUNT_FIELD, "$" + COUNT_FIELD)
                                        .append("_id", 0))
                        ).as(COUNTS_BY_ROLE_AND_STATE_FIELD)
                        .and(
                                Aggregation.group(FORMAT_FIELD).count().as(COUNT_FIELD),
                                Aggregation.project(COUNT_FIELD).andExpression("_id").as(KEY_FIELD).andExclude("_id")
                        ).as(COUNTS_BY_FORMAT_FIELD)
                        .and(
                                Aggregation.group(IS_DOWNLOADED_FIELD).count().as(COUNT_FIELD),
                                context -> new Document("$project", new Document(KEY_FIELD, new Document("$cond", List.of(
                                        "$_id",
                                        DOWNLOADED_TRUE,
                                        DOWNLOADED_FALSE
                                )))
                                        .append(COUNT_FIELD, "$" + COUNT_FIELD)
                                        .append("_id", 0))
                        ).as(DOWNLOADED_COUNTS_FIELD)
                        .and(
                                Aggregation.group(IS_DOWNLOAD_IN_PROGRESS_FIELD).count().as(COUNT_FIELD),
                                context -> new Document("$project", new Document(KEY_FIELD, new Document("$cond", List.of(
                                        "$_id",
                                        DOWNLOAD_IN_PROGRESS_TRUE,
                                        DOWNLOAD_IN_PROGRESS_FALSE
                                )))
                                        .append(COUNT_FIELD, "$" + COUNT_FIELD)
                                        .append("_id", 0))
                        ).as(DOWNLOAD_IN_PROGRESS_COUNTS_FIELD)
                        .and(Aggregation.count().as(COUNT_FIELD)).as(TOTAL_FIELD),
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

    private List<KeyCount> getCounts(Document snapshot, String fieldName) {
        Object value = snapshot.get(fieldName);
        if (!(value instanceof List<?> rawCounts)) {
            return List.of();
        }
        return rawCounts.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(this::toKeyCount)
                .flatMap(Optional::stream)
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
        Object total = snapshot.get(TOTAL_FIELD);
        if (!(total instanceof List<?> totalDocuments) || totalDocuments.isEmpty()) {
            return 0L;
        }
        Object firstEntry = totalDocuments.get(0);
        if (!(firstEntry instanceof Document totalDocument)) {
            return 0L;
        }
        return extractLong(totalDocument.get(COUNT_FIELD));
    }

    private long extractLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
