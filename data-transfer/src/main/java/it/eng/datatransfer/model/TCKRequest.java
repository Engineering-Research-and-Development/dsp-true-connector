package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class TCKRequest implements Serializable {

    private static final long serialVersionUID = 4451699406021801277L;

    @JsonProperty(DSpaceConstants.AGREEMENT_ID)
    private String agreementId;
    @JsonProperty(DSpaceConstants.FORMAT)
    private String format;
    @JsonProperty(DSpaceConstants.PROVIDER_PID)
    private String providerId;
    @JsonProperty("connectorAddress")
    private String connectorAddress;
}
