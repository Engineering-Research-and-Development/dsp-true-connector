package it.eng.connector.service;

import it.eng.connector.model.wellknown.Auth;
import it.eng.connector.model.wellknown.Version;
import it.eng.connector.model.wellknown.VersionResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class DSpaceVersionService {

    public VersionResponse getVersion() {

        return VersionResponse.Builder.newInstance()
                .protocolVersions(Collections.singletonList(Version.Builder.newInstance()
                        .version("2024-01")
                        .path("/path/to/api")
                        .auth(Auth.Builder.newInstance()
                                .protocol("https")
                                .version("2024-01")
                                .build())
                        .build()))
                .build();
    }
}
