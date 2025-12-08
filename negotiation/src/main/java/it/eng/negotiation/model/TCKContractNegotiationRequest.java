package it.eng.negotiation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TCKContractNegotiationRequest {

    private String datasetId;
    private String offerId;
    private String providerId;
    private String connectorAddress;
}
