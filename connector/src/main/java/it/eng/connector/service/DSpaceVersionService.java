package it.eng.connector.service;

import it.eng.connector.model.wellknown.Auth;
import it.eng.connector.model.wellknown.Version;
import it.eng.connector.model.wellknown.VersionResponse;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.service.ApplicationPropertiesService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DSpaceVersionService {

    public final static String DSPACE_VERSION_PROPERTY_GROUP = "application.dspace.version";

    private static final String PATH_KEY = "application.dspace.version.path";
    private static final String IDENTIFIER_TYPE_KEY = "application.dspace.version.identifierType";
    private static final String SERVICE_ID_KEY = "application.dspace.version.serviceId";

    private static final String AUTH_PROTOCOL_KEY = "application.dspace.version.auth.protocol";
    private static final String AUTH_PROTOCOL_VERSION = "application.dspace.version.auth.version";
    private static final String AUTH_PROTOCOL_PROFILE = "application.dspace.version.auth.profile";

    private final ApplicationPropertiesService applicationPropertiesService;

    public DSpaceVersionService(ApplicationPropertiesService applicationPropertiesService) {
        this.applicationPropertiesService = applicationPropertiesService;
    }

    public VersionResponse getVersion() {
        log.info("getVersion");
        String path;
        String identifierType;
        String serviceId;
        String authProtocol;
        String authVersion;
        String authProfile;

        List<ApplicationProperty> dspaceVersionProperties = applicationPropertiesService.getProperties(DSPACE_VERSION_PROPERTY_GROUP);

        Map<String, String> props = dspaceVersionProperties.stream()
                .filter(property -> StringUtils.isNotEmpty(property.getValue()))
                .collect(Collectors.toMap(ApplicationProperty::getKey, ApplicationProperty::getValue));

        path = props.get(PATH_KEY);
        identifierType = props.get(IDENTIFIER_TYPE_KEY);
        serviceId = props.get(SERVICE_ID_KEY);

        authProtocol = props.get(AUTH_PROTOCOL_KEY);
        authVersion = props.get(AUTH_PROTOCOL_VERSION);
        authProfile = props.get(AUTH_PROTOCOL_PROFILE);

        return VersionResponse.Builder.newInstance()
                .protocolVersions(Collections.singletonList(Version.Builder.newInstance()
                        .version("2025-1")
                        .path(path)
                        .identifierType(identifierType)
                        .serviceId(serviceId)
                        .auth(Auth.Builder.newInstance()
                                .protocol(authProtocol)
                                .version(authVersion)
                                .profile(StringUtils.isNotBlank(authProfile) ? Arrays.stream(authProfile.split(",")).toList() : null)
                                .build())
                        .build()))
                .build();
    }
}
