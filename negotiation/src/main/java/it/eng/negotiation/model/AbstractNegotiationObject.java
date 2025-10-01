package it.eng.negotiation.model;

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

@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.TYPE, DSpaceConstants.ID, DSpaceConstants.CONSUMER_PID, DSpaceConstants.PROVIDER_PID}, alphabetic = true)
public abstract class AbstractNegotiationObject implements Serializable {


    @Serial
    private static final long serialVersionUID = 1L;


    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DSPACE_2025_01_CONTEXT);

    @Getter
    @NotNull
    protected String providerPid;

    @NotNull
    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public abstract String getType();

    protected String createNewPid() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    protected String createNewId() {
        return UUID.randomUUID().toString();
    }
}
