package it.eng.tools.auth.keycloak.realm;

import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class RealmCredentialsStartupCheckTest {

    @Test
    @DisplayName("Checks credentials only for tenants that have a realm")
    void checksOnlyTenantsWithRealm(CapturedOutput output) {
        TenantService service = mock(TenantService.class);
        Tenant bound = Tenant.Builder.newInstance().id("a").name("A").participantId("urn:a").realm("r").build();
        Tenant unbound = Tenant.Builder.newInstance().id("b").name("B").participantId("urn:b").build();
        when(service.findAllAsList()).thenReturn(List.of(bound, unbound));

        new RealmCredentialsStartupCheck(service).warnAboutMissingCredentials();

        verify(service).findAllAsList();
        verifyNoMoreInteractions(service);

        assertThat(output.getAll())
                .contains("Tenant 'a' is bound to realm 'r' but has no realm credentials configured.")
                .doesNotContain("Tenant 'b'");
    }
}