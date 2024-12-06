package it.eng.catalog.service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.model.ArtifactRequest;
import it.eng.catalog.model.Dataset;
import it.eng.tools.model.Artifact;
import it.eng.tools.repository.ArtifactRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ArtifactService {

	// taken from GridFsResource.CONTENT_TYPE_FIELD
	private static final String CONTENT_TYPE_FIELD = "_contentType";
	private static final String DATASET_ID_METADATA = "datasetId";
	
	private final MongoTemplate mongoTemplate;
	private final DatasetService datasetService;
	private final ArtifactRepository artifactRepository;
	
	public List<Artifact> getArtifacts(String artifactId) {
		if(StringUtils.isNotBlank(artifactId)) {
			return artifactRepository.findById(artifactId)
					.stream()
					.collect(Collectors.toList());
		}
		return artifactRepository.findAll();
	}
	
	public ArtifactService(MongoTemplate mongoTemplate, DatasetService datasetService, ArtifactRepository artifactRepository) {
		super();
		this.mongoTemplate = mongoTemplate;
		this.datasetService = datasetService;
		this.artifactRepository = artifactRepository;
	}

	public Artifact uploadArtifact(MultipartFile file, String datasetId, ArtifactRequest artifactRequest) {
		switch (artifactRequest.getArtifactType()) {
		case FILE:
			return storeFile(file, datasetId, artifactRequest);
		case EXTERNAL:
			return insertExternalArtifact(datasetId, artifactRequest);
		default:
			log.warn("Artifact type not supported {}", artifactRequest.getArtifactType().name());
			throw new CatalogErrorAPIException("Artifact type not supported " + artifactRequest.getArtifactType().name());
		}
	}

	private Artifact insertExternalArtifact(String datasetId, ArtifactRequest artifactRequest) {
		try {
			Artifact artifact = Artifact.Builder.newInstance()
					.artifactType(artifactRequest.getArtifactType())
					.value(artifactRequest.getValue())
					.build();
			artifact = artifactRepository.save(artifact);
			updateDataset(datasetId, artifact.getId());
			return artifact;
		} catch (Exception e) {
			log.error("Failed to insert external artifact", e);
			throw new CatalogErrorAPIException("Failed to insert external artifact. " + e.getLocalizedMessage());
		}
	}

	private Artifact storeFile(MultipartFile file, String datasetId, ArtifactRequest artifactRequest) {
		try {
			if (file.isEmpty()) {
				throw new CatalogErrorAPIException("Failed to store empty file.");
			}
			GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
			Document doc = new Document();
			//TODO check what happens if file.getContentType() is null
			doc.append(CONTENT_TYPE_FIELD, file.getContentType());
			doc.append(DATASET_ID_METADATA, datasetId);
			GridFSUploadOptions options = new GridFSUploadOptions()
			        .chunkSizeBytes(1048576) // 1MB chunk size
			        .metadata(doc);
			ObjectId fileId = gridFSBucket.uploadFromStream(file.getOriginalFilename(), file.getInputStream(), options);
			Artifact artifact = Artifact.Builder.newInstance()
					.artifactType(artifactRequest.getArtifactType())
					.value(fileId.toHexString())
					.contentType(file.getContentType())
					.filename(file.getOriginalFilename())
					.build();
			artifact = artifactRepository.save(artifact);
			updateDataset(datasetId, artifact.getId());
			return artifact;
		}
		catch (IOException e) {
			log.error("Error while uploading file", e);
			throw new CatalogErrorAPIException("Failed to store file. " + e.getLocalizedMessage());
		}
	}
	
	private void updateDataset(String datasetId, String artifactId) {
		Dataset dataset = datasetService.getDatasetByIdForApi(datasetId);
		// TODO not most elegant solution to change one field since we do not have setters
		try {
			Field artifactIdField = dataset.getClass().getDeclaredField("artifactId");
			artifactIdField.setAccessible(true);
			artifactIdField.set(dataset, artifactId);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			log.error("Error while updating dataset with artifact reference", e);
			throw new CatalogErrorAPIException("Failed to update dataset.artifactId. " + e.getLocalizedMessage());
		}
		datasetService.updateDataset(datasetId, dataset);
	}
	
}
