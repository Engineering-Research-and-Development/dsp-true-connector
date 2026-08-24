package it.eng.tools.rest.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.tools.model.Tenant;
import it.eng.tools.serializer.ToolsSerializer;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@Component
public class PlainTenantAssembler implements RepresentationModelAssembler<Tenant, EntityModel<Object>> {

    @Override
    public EntityModel<Object> toModel(Tenant entity) {
        return EntityModel.of(entity,
                linkTo(methodOn(TenantAPIController.class).getTenantById(entity.getId())).withSelfRel());
    }
}
