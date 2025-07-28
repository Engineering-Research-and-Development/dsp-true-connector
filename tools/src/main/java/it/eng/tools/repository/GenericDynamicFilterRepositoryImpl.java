package it.eng.tools.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
public class GenericDynamicFilterRepositoryImpl<T, ID> implements GenericDynamicFilterRepository<T, ID> {

    private final MongoTemplate mongoTemplate;

    public GenericDynamicFilterRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<T> findWithDynamicFilters(Map<String, Object> filters, Class<T> entityClass, Pageable pageable) {
        Query query = new Query();

        // Build criteria based on value types - no null checks needed (filter builder handles this)
        filters.forEach((fieldName, value) -> {
            Criteria criteria = buildCriteriaByValueType(fieldName, value);
            query.addCriteria(criteria);
        });

        log.debug("Executing MongoDB query: {}", query);

        // Get total count before applying pagination
        long total = mongoTemplate.count(query, entityClass);

        // Apply pagination and sorting to query
        query.with(pageable);
        List<T> content = mongoTemplate.find(query, entityClass);
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Build criteria based on VALUE TYPE, not field name.
     *
     * @param fieldName the MongoDB field name to create criteria for
     * @param value     the value to match, type determines the criteria strategy
     * @return MongoDB criteria configured for the value type
     */
    private Criteria buildCriteriaByValueType(String fieldName, Object value) {
        // Handle different value types based on actual object type
        if (value instanceof Instant) {
            return Criteria.where(fieldName).is(value);
        }

        if (value instanceof LocalDateTime) {
            // Convert to Instant for MongoDB storage
            Instant instant = ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
            return Criteria.where(fieldName).is(instant);
        }

        if (value instanceof LocalDate) {
            // Convert to start of day Instant
            Instant instant = ((LocalDate) value).atStartOfDay().toInstant(ZoneOffset.UTC);
            return Criteria.where(fieldName).is(instant);
        }

        if (value instanceof Boolean) {
            return Criteria.where(fieldName).is(value);
        }

        if (value instanceof Number) {
            return Criteria.where(fieldName).is(value);
        }

        if (value instanceof String) {
            return Criteria.where(fieldName).is(value);
        }

        if (value instanceof Map) {
            // Handle range queries (for dates, numbers, etc.)
            return buildRangeCriteria(fieldName, (Map<String, Object>) value);
        }

        if (value instanceof Collection) {
            // Handle IN queries (multiple values for same field)
            return Criteria.where(fieldName).in((Collection<?>) value);
        }

        // Default case - exact match
        return Criteria.where(fieldName).is(value);
    }

    private Criteria buildRangeCriteria(String fieldName, Map<String, Object> rangeValue) {
        Criteria criteria = Criteria.where(fieldName);

        // Apply range operators based on keys
        rangeValue.forEach((operator, operatorValue) -> {
            if (operatorValue != null) {
                switch (operator.toLowerCase()) {
                    case "gte", "from", "after" -> criteria.gte(operatorValue);
                    case "lte", "to", "before" -> criteria.lte(operatorValue);
                    case "gt" -> criteria.gt(operatorValue);
                    case "lt" -> criteria.lt(operatorValue);
                    case "ne", "not" -> criteria.ne(operatorValue);
                    case "in" -> {
                        if (operatorValue instanceof Collection) {
                            criteria.in((Collection<?>) operatorValue);
                        }
                    }
                    default -> log.warn("Unknown range operator: {}", operator);
                }
            }
        });

        return criteria;
    }
}
