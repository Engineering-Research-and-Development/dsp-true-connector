package it.eng.tools.rest.api;

import it.eng.tools.model.Tenant;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PlainTenantAssembler implements RepresentationModelAssembler<Tenant, EntityModel<Object>> {

    @Override
    public EntityModel<Object> toModel(Tenant entity) {
        return EntityModel.of(entity,
                linkTo(methodOn(TenantAPIController.class).getTenantById(entity.getId())).withSelfRel());
    }
}
