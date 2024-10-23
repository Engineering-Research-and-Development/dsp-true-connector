package it.eng.catalog.service;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.model.Artifact;
import it.eng.catalog.model.Dataset;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ArtifactService {

	private static final String DATASET_ID_METADATA = "datasetId";
	
	private final MongoTemplate mongoTemplate;
	private final DatasetService datasetService;
	
	public ArtifactService(MongoTemplate mongoTemplate, DatasetService datasetService) {
		super();
		this.mongoTemplate = mongoTemplate;
		this.datasetService = datasetService;
	}

	public ObjectId storeFile(MultipartFile file, String datasetId) {
		try {
			if (file.isEmpty()) {
				throw new CatalogErrorAPIException("Failed to store empty file.");
			}
			GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
			Document doc = new Document();
			doc.append(HttpHeaders.CONTENT_TYPE, file.getContentType());
			doc.append(DATASET_ID_METADATA, datasetId);
			GridFSUploadOptions options = new GridFSUploadOptions()
			        .chunkSizeBytes(1048576) // 1MB chunk size
			        .metadata(doc);
			try (InputStream inputStream = file.getInputStream()) {
				   ObjectId objId = gridFSBucket.uploadFromStream(file.getOriginalFilename(), inputStream, options);
				   Dataset dataset = datasetService.getDatasetById(datasetId);
				   // TODO not most elegant solution to change one field since we do not have setters
				   Field fileIdField = dataset.getClass().getDeclaredField("fileId");
				   fileIdField.setAccessible(true);
				   fileIdField.set(dataset, objId.toHexString());
				   datasetService.updateDataset(datasetId, dataset);
				   return objId;
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
				log.error("Error while updating dataset with file reference", e);
				throw new CatalogErrorAPIException("Failed to update dataset.fileId. " + e.getLocalizedMessage());
			}
		}
		catch (IOException e) {
			log.error("Error while uploading file to dataset", e);
			throw new CatalogErrorAPIException("Failed to store file. " + e.getLocalizedMessage());
		}
	}
	
	public List<Artifact> listArtifacts(String artifactId) {
		Bson query = null;
		if(StringUtils.isNotBlank(artifactId)) {
			ObjectId fileId = new ObjectId(artifactId);
			query = Filters.eq("_id", fileId);
		} else {
			query = Filters.empty();
		}
		
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		List<Artifact> result = new ArrayList<>();
			gridFSBucket.find(query)
			.forEach(new Consumer<GridFSFile>() {
			    @Override
			    public void accept(final GridFSFile gridFSFile) {
			    	Artifact artifact = new Artifact();
			    	artifact.setId(gridFSFile.getObjectId().toHexString());
			    	artifact.setFilename(gridFSFile.getFilename());
			    	artifact.setLength(gridFSFile.getLength());
			    	artifact.setUploadDate(gridFSFile.getUploadDate().toString());
			    	artifact.setContentType(gridFSFile.getMetadata().getString(HttpHeaders.CONTENT_TYPE));
			    	artifact.setDatasetId(gridFSFile.getMetadata().getString(DATASET_ID_METADATA));
			    	result.add(artifact);
			    }
			});
			return result;
	}
	
}
