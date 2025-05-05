package it.eng.catalog.rest.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.DataService;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.DataServiceService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
	path = ApiEndpoints.CATALOG_DATA_SERVICES_V1)
@Slf4j
public class DataServiceAPIController {

    DataServiceService dataServiceService;

    public DataServiceAPIController(DataServiceService dataService) {
        super();
        this.dataServiceService = dataService;
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> getDataServiceById(@PathVariable String id) {
        log.info("Fetching data service with id: '" + id + "'");
        DataService dataService = dataServiceService.getDataServiceById(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(dataService), "Fetched data service"));
    }

    @GetMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> getAllDataServices() {
        log.info("Fetching all data services");
        var dataServices = dataServiceService.getAllDataServices();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(dataServices), "Fetched all data service"));
    }

    @PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> saveDataService(@RequestBody String dataService) {
        DataService ds = CatalogSerializer.deserializePlain(dataService, DataService.class);

        log.info("Saving new data service");

        DataService storedDataService = dataServiceService.saveDataService(ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(storedDataService), "Data service saved"));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<Void>> deleteDataService(@PathVariable String id) {
        log.info("Deleting data service with id: " + id);

        dataServiceService.deleteDataService(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Data service deleted successfully"));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> updateDataService(@PathVariable String id, @RequestBody String dataService) {
        DataService ds = CatalogSerializer.deserializePlain(dataService, DataService.class);

        log.info("Updating data service with id: " + id);

        DataService updatedDataService = dataServiceService.updateDataService(id, ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(updatedDataService), "Data service updated"));
    }
}
