package it.eng.datatransfer.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * Controller for data migration operations.
 */
@RestController
@RequestMapping("/api/migration")
@Slf4j
public class DataMigrationController {

    private final DataMigrationService dataMigrationService;

    @Autowired
    public DataMigrationController(DataMigrationService dataMigrationService) {
        this.dataMigrationService = dataMigrationService;
    }

    /**
     * Endpoint to manually trigger data migration from MongoDB GridFS to S3.
     * 
     * @return a response with the migration result
     */
    @PostMapping("/gridfs-to-s3")
    public ResponseEntity<String> migrateGridFsToS3() {
        log.info("Received request to migrate data from GridFS to S3");
        String result = dataMigrationService.migrateData();
        return ResponseEntity.ok(result);
    }
}
