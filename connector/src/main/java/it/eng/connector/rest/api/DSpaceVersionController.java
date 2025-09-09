package it.eng.connector.rest.api;

import it.eng.connector.model.wellknown.VersionResponse;
import it.eng.connector.service.DSpaceVersionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DSpaceVersionController {

    private final DSpaceVersionService dSpaceVersionService;

    public DSpaceVersionController(DSpaceVersionService dSpaceVersionService) {
        this.dSpaceVersionService = dSpaceVersionService;
    }

    @GetMapping(path = "/.well-known/dspace-version", produces = MediaType.APPLICATION_JSON_VALUE)
    public VersionResponse getVersion() {
        return dSpaceVersionService.getVersion();
    }
}
