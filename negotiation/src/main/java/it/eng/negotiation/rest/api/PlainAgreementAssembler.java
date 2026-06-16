package it.eng.negotiation.rest.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.serializer.NegotiationSerializer;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Assembler that converts an {@link Agreement} into an {@link EntityModel} with a self-link.
 */
@Component
public class PlainAgreementAssembler implements RepresentationModelAssembler<Agreement, EntityModel<Object>> {

    @Override
    public EntityModel<Object> toModel(Agreement entity) {
        var plainJson = (ObjectNode) NegotiationSerializer.serializePlainJsonNode(entity);
        Map<String, Object> content = new ObjectMapper().convertValue(plainJson, new TypeReference<>() {});
        return EntityModel.of(content,
                linkTo(methodOn(AgreementAPIController.class).getAgreementById(entity.getId())).withSelfRel());
    }
}
