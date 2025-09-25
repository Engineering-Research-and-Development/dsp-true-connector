package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import it.eng.tools.model.DSpaceConstants;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public abstract class AbstractCatalogObject implements Serializable {

    private static final long serialVersionUID = 6931659075077465603L;

    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DSPACE_2025_01_CONTEXT);

    /**
     * Can be optional.
     * If the message does not include a consumerPid, a new contract negotiation will be created on consumer
     * side and the consumer selects an appropriate consumerPid
     */

    /**
     * Returns dspace protocol type.
     *
     * @return type
     */
    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public abstract String getType();

    protected String createNewPid() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    protected String createNewId() {
        return UUID.randomUUID().toString();
    }
}
