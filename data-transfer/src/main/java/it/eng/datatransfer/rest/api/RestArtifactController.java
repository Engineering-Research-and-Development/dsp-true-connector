package it.eng.datatransfer.rest.api;

import it.eng.datatransfer.service.api.RestArtifactService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/artifacts")
@Slf4j
public class RestArtifactController {

    private final RestArtifactService restArtifactService;

    public RestArtifactController(RestArtifactService restArtifactService) {
        super();
        this.restArtifactService = restArtifactService;
    }

    /**
     * Fetch artifact for transactionId.
     *
     * @param response      HttpServlerTesponse that will be updated with data
     * @param authorization
     * @param transactionId Base64.urlEncoded(consumerPid|providerPid) from TransferProcess message
     */
    @GetMapping(path = "/{transactionId}")
    public void getArtifact(HttpServletResponse response,
                            @RequestHeader(required = false) String authorization,
                            @PathVariable String transactionId) {

        log.info("Starting data download");
        restArtifactService.getArtifact(transactionId, response);
    }
}
