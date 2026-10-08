package it.eng.tools.rest.api;

import it.eng.tools.auth.keycloak.realm.RealmCredentialsService;
import it.eng.tools.model.Tenant;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Assembles plain tenant representations, including the computed credentials flag.
 */
@Component
public class PlainTenantAssembler implements RepresentationModelAssembler<Tenant, EntityModel<Object>> {

    private final RealmCredentialsService realmCredentialsService;

    /**
     * Constructs the assembler.
     *
     * @param realmCredentialsService the service used to compute the credentials flag
     */
    public PlainTenantAssembler(RealmCredentialsService realmCredentialsService) {
        this.realmCredentialsService = realmCredentialsService;
    }

    @Override
    public EntityModel<Object> toModel(Tenant entity) {
        entity.markCredentialsConfigured(realmCredentialsService.exists(entity.getId()));
        return EntityModel.of(entity,
                linkTo(methodOn(TenantAPIController.class).getTenantById(entity.getId())).withSelfRel());
    }
}
