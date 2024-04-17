package it.eng.negotiation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.negotiation.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ContractNegotiationConsumerService {

    @Value("${application.connectorid}")
    String connectroId;

    /**
     * {
     * "@context": "https://w3id.org/dspace/v0.8/context.json",
     * "@type": "dspace:ContractNegotiation",
     * "dspace:providerPid": "urn:uuid:dcbf434c-eacf-4582-9a02-f8dd50120fd3",
     * "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
     * "dspace:state" :"OFFERED"
     * }
     *
     * @param contractOfferMessage
     * @return
     */

    public JsonNode processContractOffer(ContractOfferMessage contractOfferMessage) {
        //TODO consumer side only - handle consumerPid and providerPid
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(contractOfferMessage.getProviderPid())
                .providerPid(connectroId)
                .state(ContractNegotiationState.OFFERED)
                .build();

        return Serializer.serializeProtocolJsonNode(contractNegotiation);
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param consumerPid
     * @param contractOfferMessage
     * @return
     */

    public JsonNode handleNegotiationOfferConsumer(String consumerPid, ContractOfferMessage contractOfferMessage) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode testNode = mapper.createObjectNode();
        return testNode;
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param consumerPid
     * @param contractAgreementMessage
     * @return
     */

    public JsonNode handleAgreement(String consumerPid, ContractAgreementMessage contractAgreementMessage) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode testNode = mapper.createObjectNode();
        return testNode;
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param consumerPid
     * @param contractNegotiationEventMessage
     * @return
     */

    public JsonNode handleEventsResponse(String consumerPid, ContractNegotiationEventMessage contractNegotiationEventMessage) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode testNode = mapper.createObjectNode();
        return testNode;
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param consumerPid
     * @param contractNegotiationTerminationMessage
     * @return
     */

    public JsonNode handleTerminationResponse(String consumerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode testNode = mapper.createObjectNode();
        return testNode;
    }

}
