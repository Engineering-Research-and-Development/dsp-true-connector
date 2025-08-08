package it.eng.datatransfer.rest.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.serializer.TransferSerializer;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PlainTransferProcessAssembler implements RepresentationModelAssembler<TransferProcess, EntityModel<Object>> {

    @Override
    public EntityModel<Object> toModel(TransferProcess entity) {
        ObjectNode plainJson = (ObjectNode) TransferSerializer.serializePlainJsonNode(entity);
        Map<String, Object> content = new ObjectMapper().convertValue(plainJson, new TypeReference<Map<String, Object>>() {
        });
        return EntityModel.of(content, linkTo(methodOn(DataTransferAPIController.class).getTransferProcessById(entity.getId())).withSelfRel());
    }
}
