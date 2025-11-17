package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Getter
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.TYPE, DSpaceConstants.ID, DSpaceConstants.CONSUMER_PID, DSpaceConstants.PROVIDER_PID}, alphabetic = true)
public abstract class AbstractTransferMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = -3150306747585657302L;

    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DSPACE_2025_01_CONTEXT);

    @NotNull
    protected String consumerPid;

    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public abstract String getType();

    protected String createNewId() {
        return UUID.randomUUID().toString();
    }

    protected String createNewPid() {
        return "urn:uuid:" + UUID.randomUUID();
    }
}
