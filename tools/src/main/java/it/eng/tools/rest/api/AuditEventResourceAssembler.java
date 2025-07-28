package it.eng.tools.rest.api;

import it.eng.tools.event.AuditEvent;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class AuditEventResourceAssembler implements RepresentationModelAssembler<AuditEvent, EntityModel<Object>> {

    @Override
    public EntityModel<Object> toModel(AuditEvent entity) {
        return EntityModel.of(entity,
                linkTo(methodOn(AuditEventController.class).getAuditEventById(entity.getId())).withSelfRel());
    }
}
