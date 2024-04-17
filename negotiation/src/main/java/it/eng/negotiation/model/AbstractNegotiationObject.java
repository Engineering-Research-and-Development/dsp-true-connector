package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public abstract class AbstractNegotiationObject {

    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private String context = DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE;

    /**
     * Can be optional.
     * If the message does not include a consumerPid, a new contract negotiation will be created on consumer
     * side and the consumer selects an appropriate consumerPid
     */

    @NotNull
    @JsonProperty(DSpaceConstants.DSPACE_PROVIDER_PID)
    protected String providerPid;

    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public abstract String getType();

    public String getProviderPid() {
        return providerPid;
    }

    protected String createNewPid() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    protected String createNewId() {
        return UUID.randomUUID().toString();
    }
}
