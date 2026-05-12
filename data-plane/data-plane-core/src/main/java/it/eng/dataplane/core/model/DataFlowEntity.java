package it.eng.dataplane.core.model;

import it.eng.dataplane.api.model.DataFlowState;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

/**
 * MongoDB document representing a data flow managed by this Data Plane instance.
 */
@Getter
@Setter
@Document("data_flows")
public class DataFlowEntity {

    @Id
    private String id;

    @Indexed(unique = true)
    private String processId;

    private String agreementId;
    private String datasetId;
    private String transferType;
    private String callbackAddress;
    private DataFlowState state;
    private Map<String, String> dataAddress;
    private String tenantId;
    private String participantId;
    private String counterPartyId;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
