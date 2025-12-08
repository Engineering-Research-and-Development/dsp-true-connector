package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class TCKRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 4451699406021801277L;

    @JsonProperty(DSpaceConstants.AGREEMENT_ID)
    private String agreementId;
    @JsonProperty(DSpaceConstants.FORMAT)
    private String format;
    @JsonProperty(DSpaceConstants.PROVIDER_PID)
    private String providerId;
    @JsonProperty("connectorAddress")
    private String connectorAddress;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final TCKRequest tckRequest;

        private Builder() {
            tckRequest = new TCKRequest();
        }

        public static TCKRequest.Builder newInstance() {
            return new TCKRequest.Builder();
        }

        public TCKRequest.Builder agreementId(String agreementId) {
            tckRequest.agreementId = agreementId;
            return this;
        }

        public TCKRequest.Builder format(String format) {
            tckRequest.format = format;
            return this;
        }

        public TCKRequest.Builder providerId(String providerId) {
            tckRequest.providerId = providerId;
            return this;
        }

        public TCKRequest.Builder connectorAddress(String connectorAddress) {
            tckRequest.connectorAddress = connectorAddress;
            return this;
        }

        public TCKRequest build() {
            return tckRequest;
        }
    }

}
