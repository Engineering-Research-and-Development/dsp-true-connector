package it.eng.connector.rest.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.connector.model.User;
import it.eng.tools.serializer.ToolsSerializer;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PlainUserAssembler implements RepresentationModelAssembler<User, EntityModel<Object>> {

    @Override
    public EntityModel<Object> toModel(User entity) {
        ObjectNode plainJson = (ObjectNode) ToolsSerializer.serializePlainJsonNode(entity);
        // Remove sensitive information from the response
        plainJson.remove("password");
        
        Map<String, Object> content = new ObjectMapper().convertValue(plainJson, new TypeReference<Map<String, Object>>() {
        });
        return EntityModel.of(content, linkTo(methodOn(UserManagementAPIController.class).getUserById(entity.getId())).withSelfRel());
    }
}
