package it.eng.negotiation.rest.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.serializer.NegotiationSerializer;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PlainContractNegotiationAssembler implements RepresentationModelAssembler<ContractNegotiation, EntityModel<Object>> {

    @Override
    public EntityModel<Object> toModel(ContractNegotiation entity) {
        ObjectNode plainJson = (ObjectNode) NegotiationSerializer.serializePlainJsonNode(entity);
        Map<String, Object> content = new ObjectMapper().convertValue(plainJson, new TypeReference<Map<String, Object>>() {
        });
        return EntityModel.of(content);
    }
}
