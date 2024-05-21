package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.DataService;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.DataServiceService;
import lombok.extern.java.Log;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/dataservice")
@Log
public class DataServiceAPIController {

    DataServiceService dataServiceService;

    public DataServiceAPIController(DataServiceService dataService) {
        super();
        this.dataServiceService = dataService;
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<JsonNode> getDataServiceById(@PathVariable String id) {
        log.info("Fetching data service with id: '" + id + "'");
        DataService dataService = dataServiceService.getDataServiceById(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(dataService));
    }

    @GetMapping
    public ResponseEntity<JsonNode> getAllDataServices() {
        log.info("Fetching all data services");
        var dataServices = dataServiceService.getAllDataServices();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(dataServices));
    }

    @PostMapping
    public ResponseEntity<JsonNode> saveDataService(@RequestBody String dataService) {
        DataService ds = Serializer.deserializePlain(dataService, DataService.class);

        log.info("Saving new data service");

        DataService storedDataService = dataServiceService.saveDataService(ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(storedDataService));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<String> deleteDataService(@PathVariable String id) {
        log.info("Deleting data service with id: " + id);

        dataServiceService.deleteDataService(id);

        return ResponseEntity.ok().body("Data service deleted successfully");
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<JsonNode> updateDataService(@PathVariable String id, @RequestBody String dataService) {
        DataService ds = Serializer.deserializePlain(dataService, DataService.class);

        log.info("Updating data service with id: " + id);

        DataService updatedDataService = dataServiceService.updateDataService(id, ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(updatedDataService));
    }
}
