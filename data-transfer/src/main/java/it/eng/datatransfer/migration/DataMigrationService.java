package it.eng.datatransfer.migration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import org.apache.commons.io.IOUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;

import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.repository.TransferProcessRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for migrating data from MongoDB GridFS to S3.
 */
@Service
@Slf4j
public class DataMigrationService {

    private final MongoTemplate mongoTemplate;
    private final GridFsTemplate gridFsTemplate;
    private final S3ClientService s3ClientService;
    private final S3Properties s3Properties;
    private final TransferProcessRepository transferProcessRepository;
    
    private static final String CONTENT_TYPE_FIELD = "contentType";
    private static final String DATASET_ID_METADATA = "datasetId";

    @Autowired
    public DataMigrationService(MongoTemplate mongoTemplate, 
                               GridFsTemplate gridFsTemplate,
                               S3ClientService s3ClientService,
                               S3Properties s3Properties,
                               TransferProcessRepository transferProcessRepository) {
        this.mongoTemplate = mongoTemplate;
        this.gridFsTemplate = gridFsTemplate;
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
        this.transferProcessRepository = transferProcessRepository;
    }

    /**
     * Migrates data from MongoDB GridFS to S3 when the application starts.
     * This method is triggered by the ApplicationReadyEvent.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void migrateDataOnStartup() {
        log.info("Starting data migration from MongoDB GridFS to S3...");
        
        try {
            // Create S3 bucket if it doesn't exist
            String bucketName = s3Properties.getBucketName();
            if (!s3ClientService.bucketExists(bucketName)) {
                s3ClientService.createBucket(bucketName);
                log.info("Created S3 bucket: {}", bucketName);
            }
            
            // Get all transfer processes with downloaded data
            List<TransferProcess> transferProcesses = transferProcessRepository.findByIsDownloaded(true);
            log.info("Found {} transfer processes with downloaded data", transferProcesses.size());
            
            int migratedCount = 0;
            int errorCount = 0;
            
            for (TransferProcess transferProcess : transferProcesses) {
                try {
                    // Skip if the dataId is not a valid ObjectId (might already be migrated)
                    if (!ObjectId.isValid(transferProcess.getDataId())) {
                        log.info("Skipping transfer process {} as it might already be migrated", transferProcess.getId());
                        continue;
                    }
                    
                    // Get the file from GridFS
                    ObjectId fileId = new ObjectId(transferProcess.getDataId());
                    GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
                    GridFSFile gridFSFile = gridFSBucket.find(Filters.eq("_id", fileId)).first();
                    
                    if (gridFSFile == null) {
                        log.warn("File not found in GridFS for transfer process: {}", transferProcess.getId());
                        continue;
                    }
                    
                    // Get file content and metadata
                    GridFsResource resource = gridFsTemplate.getResource(gridFSFile);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    IOUtils.copy(resource.getInputStream(), outputStream);
                    byte[] fileData = outputStream.toByteArray();
                    
                    Document metadata = gridFSFile.getMetadata();
                    String contentType = metadata != null && metadata.containsKey(CONTENT_TYPE_FIELD) 
                            ? metadata.getString(CONTENT_TYPE_FIELD) 
                            : "application/octet-stream";
                    
                    // Upload to S3
                    String objectKey = transferProcess.getId(); // Use transfer process ID as object key
                    s3ClientService.uploadFile(bucketName, objectKey, fileData, contentType, IConstants.ATTACHMENT_FILENAME+resource.getFilename());
                    
                    // Update transfer process to point to S3
                    TransferProcess updatedTransferProcess = TransferProcess.Builder.newInstance()
                            .id(transferProcess.getId())
                            .agreementId(transferProcess.getAgreementId())
                            .consumerPid(transferProcess.getConsumerPid())
                            .providerPid(transferProcess.getProviderPid())
                            .callbackAddress(transferProcess.getCallbackAddress())
                            .dataAddress(transferProcess.getDataAddress())
                            .isDownloaded(true)
                            .dataId(objectKey) // Store object key instead of MongoDB ObjectId
                            .format(transferProcess.getFormat())
                            .state(transferProcess.getState())
                            .role(transferProcess.getRole())
                            .datasetId(transferProcess.getDatasetId())
                            .createdBy(transferProcess.getCreatedBy())
                            .lastModifiedBy(transferProcess.getLastModifiedBy())
                            .version(transferProcess.getVersion())
                            .build();
                    
                    transferProcessRepository.save(updatedTransferProcess);
                    migratedCount++;
                    
                    log.info("Migrated file for transfer process: {}", transferProcess.getId());
                } catch (Exception e) {
                    log.error("Error migrating file for transfer process: {}", transferProcess.getId(), e);
                    errorCount++;
                }
            }
            
            log.info("Data migration completed. Migrated: {}, Errors: {}", migratedCount, errorCount);
        } catch (Exception e) {
            log.error("Error during data migration", e);
        }
    }
    
    /**
     * Manually triggers the data migration process.
     * 
     * @return a summary of the migration process
     */
    public String migrateData() {
        log.info("Manually triggering data migration from MongoDB GridFS to S3...");
        
        try {
            // Create S3 bucket if it doesn't exist
            String bucketName = s3Properties.getBucketName();
            if (!s3ClientService.bucketExists(bucketName)) {
                s3ClientService.createBucket(bucketName);
                log.info("Created S3 bucket: {}", bucketName);
            }
            
            // Get all files from GridFS
            GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
            GridFSFindIterable gridFSFiles = gridFSBucket.find();
            
            int totalFiles = 0;
            int migratedCount = 0;
            int errorCount = 0;
            
            for (GridFSFile gridFSFile : gridFSFiles) {
                totalFiles++;
                ObjectId fileId = gridFSFile.getObjectId();
                
                try {
                    // Check if this file is referenced by any transfer process
                    Query query = new Query(Criteria.where("dataId").is(fileId.toHexString()));
                    TransferProcess transferProcess = mongoTemplate.findOne(query, TransferProcess.class);
                    
                    if (transferProcess == null) {
                        log.warn("File {} not referenced by any transfer process, skipping", fileId.toHexString());
                        continue;
                    }
                    
                    // Get file content and metadata
                    GridFsResource resource = gridFsTemplate.getResource(gridFSFile);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    IOUtils.copy(resource.getInputStream(), outputStream);
                    byte[] fileData = outputStream.toByteArray();
                    
                    Document metadata = gridFSFile.getMetadata();
                    String contentType = metadata != null && metadata.containsKey(CONTENT_TYPE_FIELD) 
                            ? metadata.getString(CONTENT_TYPE_FIELD) 
                            : "application/octet-stream";
                    
                    // Upload to S3
                    String objectKey = transferProcess.getId(); // Use transfer process ID as object key
                    s3ClientService.uploadFile(bucketName, objectKey, fileData, contentType, IConstants.ATTACHMENT_FILENAME+resource.getFilename());
                    
                    // Update transfer process to point to S3
                    TransferProcess updatedTransferProcess = TransferProcess.Builder.newInstance()
                            .id(transferProcess.getId())
                            .agreementId(transferProcess.getAgreementId())
                            .consumerPid(transferProcess.getConsumerPid())
                            .providerPid(transferProcess.getProviderPid())
                            .callbackAddress(transferProcess.getCallbackAddress())
                            .dataAddress(transferProcess.getDataAddress())
                            .isDownloaded(true)
                            .dataId(objectKey) // Store object key instead of MongoDB ObjectId
                            .format(transferProcess.getFormat())
                            .state(transferProcess.getState())
                            .role(transferProcess.getRole())
                            .datasetId(transferProcess.getDatasetId())
                            .createdBy(transferProcess.getCreatedBy())
                            .lastModifiedBy(transferProcess.getLastModifiedBy())
                            .version(transferProcess.getVersion())
                            .build();
                    
                    transferProcessRepository.save(updatedTransferProcess);
                    migratedCount++;
                    
                    log.info("Migrated file for transfer process: {}", transferProcess.getId());
                } catch (IOException e) {
                    log.error("Error migrating file: {}", fileId.toHexString(), e);
                    errorCount++;
                }
            }
            
            return String.format("Data migration completed. Total files: %d, Migrated: %d, Errors: %d", 
                    totalFiles, migratedCount, errorCount);
        } catch (Exception e) {
            log.error("Error during data migration", e);
            return "Error during data migration: " + e.getMessage();
        }
    }
}
