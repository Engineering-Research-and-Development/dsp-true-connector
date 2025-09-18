package it.eng.connector.service;

import it.eng.connector.model.wellknown.VersionResponse;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.service.ApplicationPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DSpaceVersionServiceTest {

    @InjectMocks
    private DSpaceVersionService dSpaceVersionService;

    @Mock
    private ApplicationPropertiesService applicationPropertiesService;

    @Test
    public void testGetVersion() {
        when(applicationPropertiesService.getProperties(DSpaceVersionService.DSPACE_VERSION_PROPERTY_GROUP))
                .thenReturn(getApplicationProperties());

        VersionResponse response = dSpaceVersionService.getVersion();
        assertNotNull(response);
        assertEquals("/api/version", response.getProtocolVersions().get(0).getPath());
        assertEquals("DOI", response.getProtocolVersions().get(0).getIdentifierType());
        assertEquals("DSpaceService", response.getProtocolVersions().get(0).getServiceId());
        assertEquals("OAuth2", response.getProtocolVersions().get(0).getAuth().getProtocol());
        assertEquals("2.0", response.getProtocolVersions().get(0).getAuth().getVersion());
        assertTrue(response.getProtocolVersions().get(0).getAuth().getProfile().contains("Bearer"));
    }

    private List<ApplicationProperty> getApplicationProperties() {
        return Arrays.asList(
                ApplicationProperty.Builder.newInstance()
                        .key("application.dspace.version.path")
                        .value("/api/version")
                        .build(),
                ApplicationProperty.Builder.newInstance()
                        .key("application.dspace.version.identifierType")
                        .value("DOI")
                        .build(),
                ApplicationProperty.Builder.newInstance()
                        .key("application.dspace.version.serviceId")
                        .value("DSpaceService")
                        .build(),
                ApplicationProperty.Builder.newInstance()
                        .key("application.dspace.version.auth.protocol")
                        .value("OAuth2")
                        .build(),
                ApplicationProperty.Builder.newInstance()
                        .key("application.dspace.version.auth.version")
                        .value("2.0")
                        .build(),
                ApplicationProperty.Builder.newInstance()
                        .key("application.dspace.version.auth.profile")
                        .value("Bearer")
                        .build()
        );
    }
}
