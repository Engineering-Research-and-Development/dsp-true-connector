package it.eng.tools.auth.keycloak.realm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RealmCredentialsStartupCheckTest {

    @Test
    @DisplayName("Checks credentials only for tenants that have a realm")
    void checksOnlyTenantsWithRealm() {
        TenantRepository repo = mock(TenantRepository.class);
        RealmCredentialsService service = mock(RealmCredentialsService.class);
        Tenant bound = Tenant.Builder.newInstance().id("a").name("A").participantId("urn:a").realm("r").build();
        Tenant unbound = Tenant.Builder.newInstance().id("b").name("B").participantId("urn:b").build();
        when(repo.findAll()).thenReturn(List.of(bound, unbound));
        when(service.exists("a")).thenReturn(false);

        new RealmCredentialsStartupCheck(repo, service).warnAboutMissingCredentials();

        verify(service).exists("a");
        verifyNoMoreInteractions(service);
    }
}
