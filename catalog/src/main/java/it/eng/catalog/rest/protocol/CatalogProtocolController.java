package it.eng.catalog.rest.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.java.Log;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = "/catalog")
@Log
public class CatalogProtocolController {

    private final CatalogService catalogService;

    public CatalogProtocolController(CatalogService catalogService) {
        super();
        this.catalogService = catalogService;
    }

    @PostMapping(path = "/request")
    protected ResponseEntity<String> getCatalog(@RequestHeader(required = false) String authorization,
                                                @RequestBody JsonNode object) {
        log.info("Handling get catalog");
        verifyAuthorization(authorization);
        verifyRequestMessage(object);
        var catalog = catalogService.getCatalog();
        return ResponseEntity.ok().header("foo", "bar").contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializeProtocol(catalog));

    }

    @GetMapping(path = "datasets/{id}")
    public ResponseEntity<String> getDataset(@PathVariable String id) {
        log.info("Preparing dataset");

        Dataset dataSet = catalogService.getDataSetById(id).orElseThrow(() -> new CatalogErrorException("Data Set with id: " + id + " not found"));
        return ResponseEntity.ok().header("id", "bar").contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializeProtocol(dataSet));
    }

    private void verifyRequestMessage(JsonNode jsonNode) {
        if (ObjectUtils.isEmpty(jsonNode.get(DSpaceConstants.TYPE))) {
            throw new BadRequestException("Request message not present");
        }
        if (!jsonNode.get(DSpaceConstants.TYPE).asText().equals(DSpaceConstants.DSPACE + CatalogRequestMessage.class.getSimpleName())) {
            throw new BadRequestException("Not valid request message");
        }
    }

    private void verifyAuthorization(String authorization) {
        // TODO Auto-generated method stub
    }


    private String getCatalogIdFromFilter(String filter) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj;
        try {
            actualObj = mapper.readTree(filter);
            return actualObj.get("catalogId").asText();
        } catch (JsonProcessingException e) {
            log.info("No catalog in filter");
        }
        return null;
    }

}
