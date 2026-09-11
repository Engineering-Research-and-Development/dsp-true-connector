package it.eng.connector.rest.api;

import it.eng.connector.model.User;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PlainUserAssembler implements RepresentationModelAssembler<User, EntityModel<Object>> {

    @Override
    public EntityModel<Object> toModel(User entity) {
        return EntityModel.of(entity,
                linkTo(methodOn(UserAPIController.class).getUserById(entity.getId())).withSelfRel()); }
}
