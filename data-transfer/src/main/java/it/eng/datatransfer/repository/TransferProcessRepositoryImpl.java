package it.eng.datatransfer.repository;

import it.eng.datatransfer.model.TransferProcess;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * Custom repository implementation for dynamic filtering operations.
 * This class implements data access methods that require dynamic query building using MongoTemplate.
 */
@Repository
public class TransferProcessRepositoryImpl implements TransferProcessRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public TransferProcessRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Collection<TransferProcess> findWithDynamicFilters(String state, String role,
                                                              String datasetId, String providerPid,
                                                              String consumerPid) {

        Query query = new Query();

        // Add criteria only for non-null and non-empty parameters
        if (StringUtils.hasText(state)) {
            query.addCriteria(Criteria.where("state").is(state));
        }

        if (StringUtils.hasText(role)) {
            query.addCriteria(Criteria.where("role").is(role));
        }

        if (StringUtils.hasText(datasetId)) {
            query.addCriteria(Criteria.where("datasetId").is(datasetId));
        }

        if (StringUtils.hasText(providerPid)) {
            query.addCriteria(Criteria.where("providerPid").is(providerPid));
        }

        if (StringUtils.hasText(consumerPid)) {
            query.addCriteria(Criteria.where("consumerPid").is(consumerPid));
        }

        // Execute query and return results
        List<TransferProcess> results = mongoTemplate.find(query, TransferProcess.class);
        return results;
    }
} 