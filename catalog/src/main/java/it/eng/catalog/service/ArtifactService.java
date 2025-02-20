package it.eng.catalog.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

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
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ArtifactService {

	// taken from GridFsResource.CONTENT_TYPE_FIELD
	private static final String CONTENT_TYPE_FIELD = "_contentType";
	private static final String DATASET_ID_METADATA = "datasetId";
	
	private final MongoTemplate mongoTemplate;
	private final ArtifactRepository artifactRepository;
	
	public ArtifactService(MongoTemplate mongoTemplate, ArtifactRepository artifactRepository) {
		super();
		this.mongoTemplate = mongoTemplate;
		this.artifactRepository = artifactRepository;
	}

	public List<Artifact> getArtifacts(String artifactId) {
		if(StringUtils.isNotBlank(artifactId)) {
			Optional<Artifact> artifact = artifactRepository.findById(artifactId);
			if (artifact.isPresent()) {
				return List.of(artifact.get());
			} else {
				throw new ResourceNotFoundAPIException("Artifact with id " + artifactId + " not found");
			}
		}
		return artifactRepository.findAll();
	}

	public Artifact uploadArtifact(Dataset dataset, MultipartFile file, String externalURL, String authorization) {
		Artifact artifact = null;
		if (file != null) {
			ObjectId fileId = storeFile(file, dataset);
			artifact = Artifact.Builder.newInstance()
					.artifactType(ArtifactType.FILE)
					.value(fileId.toHexString())
					.contentType(file.getContentType())
					.filename(file.getOriginalFilename())
					.build();
		} else if (externalURL != null) {
			artifact = Artifact.Builder.newInstance()
					.artifactType(ArtifactType.EXTERNAL)
					.authorization(authorization)
					.value(externalURL)
					.build();
		} else {
			log.warn("Artifact and file not found");
			throw new CatalogErrorAPIException("Artifact and file not found");
		}
		artifact = artifactRepository.save(artifact);
		log.info("Inserted Artifact {}", artifact.getFilename() != null ? artifact.getFilename() : artifact.getValue());
		return artifact;
	}
	
	public void deleteOldArtifact(Artifact artifact) {
		log.info("Deleting artifact {}", artifact.getId());
		switch (artifact.getArtifactType()) {
		case EXTERNAL: {
			break;
		}
		case FILE: {
			try {
				GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
				ObjectId objectId = new ObjectId(artifact.getValue());
				gridFSBucket.delete(objectId);
			} catch (Exception e) {
				log.warn("!!!!!  Artifact {}, had no data to delete proceeding with artifact removal  !!!!!");
			}
			break;
		}
		default:
			break;
		}
		artifactRepository.delete(artifact);
	}

	private ObjectId storeFile(MultipartFile file, Dataset dataset) {
		try {
			GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
			Document doc = new Document();
			//TODO check what happens if file.getContentType() is null
			doc.append(CONTENT_TYPE_FIELD, file.getContentType());
			doc.append(DATASET_ID_METADATA, dataset.getId());
			GridFSUploadOptions options = new GridFSUploadOptions()
			        .chunkSizeBytes(1048576) // 1MB chunk size
			        .metadata(doc);
			return gridFSBucket.uploadFromStream(file.getOriginalFilename(), file.getInputStream(), options);
		}
		catch (IOException e) {
			log.error("Error while uploading file", e);
			throw new CatalogErrorAPIException("Failed to store file. " + e.getLocalizedMessage());
		}
	}
}
