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
	private final DatasetService datasetService;
	private final ArtifactRepository artifactRepository;
	
	public ArtifactService(MongoTemplate mongoTemplate, DatasetService datasetService, ArtifactRepository artifactRepository) {
		super();
		this.mongoTemplate = mongoTemplate;
		this.datasetService = datasetService;
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

	public Artifact uploadArtifact(MultipartFile file, String datasetId, String externalURL) {
		Dataset dataset = datasetService.getDatasetByIdForApi(datasetId);
		if (file != null) {
			return storeFile(file, dataset);
		} else if (externalURL != null) {
			return insertExternalArtifact(dataset, externalURL);
		}
		log.warn("Artifact and file not found");
		throw new CatalogErrorAPIException("Artifact and file not found");
	}
	
	public void deleteArtifact (String id) {
		Artifact artifact = artifactRepository.findById(id)
				.orElseThrow(() -> new CatalogErrorAPIException("Could not delete artifact, artifact with id: " + id + " does not exist"));
		deleteArtifactAndData(artifact);
		removeArtifactFromDataset(artifact);
	}

	private Artifact insertExternalArtifact(Dataset dataset, String externalURL) {
		try {
			Artifact artifact = Artifact.Builder.newInstance()
					.artifactType(ArtifactType.EXTERNAL)
					.value(externalURL)
					.build();
			artifact = artifactRepository.save(artifact);
			updateDataset(dataset, artifact);
			return artifact;
		} catch (Exception e) {
			log.error("Failed to insert external artifact", e);
			throw new CatalogErrorAPIException("Failed to insert external artifact. " + e.getLocalizedMessage());
		}
	}

	private Artifact storeFile(MultipartFile file, Dataset dataset) {
		try {
			GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
			Document doc = new Document();
			//TODO check what happens if file.getContentType() is null
			doc.append(CONTENT_TYPE_FIELD, file.getContentType());
			doc.append(DATASET_ID_METADATA, dataset.getId());
			GridFSUploadOptions options = new GridFSUploadOptions()
			        .chunkSizeBytes(1048576) // 1MB chunk size
			        .metadata(doc);
			ObjectId fileId = gridFSBucket.uploadFromStream(file.getOriginalFilename(), file.getInputStream(), options);
			Artifact artifact = Artifact.Builder.newInstance()
					.artifactType(ArtifactType.FILE)
					.value(fileId.toHexString())
					.contentType(file.getContentType())
					.filename(file.getOriginalFilename())
					.build();
			artifact = artifactRepository.save(artifact);
			updateDataset(dataset, artifact);
			return artifact;
		}
		catch (IOException e) {
			log.error("Error while uploading file", e);
			throw new CatalogErrorAPIException("Failed to store file. " + e.getLocalizedMessage());
		}
	}
	
	private void updateDataset(Dataset dataset, Artifact artifact) {
		Artifact oldArtifact = dataset.getArtifact();
		Dataset datasetWithArtifact = Dataset.Builder.newInstance()
				.id(dataset.getId())
				.artifact(artifact)
				.conformsTo(dataset.getConformsTo())
				.createdBy(dataset.getCreatedBy())
				.creator(dataset.getCreator())
				.description(dataset.getDescription())
				.distribution(dataset.getDistribution())
				.hasPolicy(dataset.getHasPolicy())
				.issued(dataset.getIssued())
				.keyword(dataset.getKeyword())
				.lastModifiedBy(dataset.getLastModifiedBy())
				.modified(dataset.getModified())
				.theme(dataset.getTheme())
				.title(dataset.getTitle())
				.version(dataset.getVersion())
				.build();
		datasetService.updateDataset(dataset, datasetWithArtifact);
		if (oldArtifact != null) {
			deleteArtifactAndData(oldArtifact);
		}
	}

	private void deleteArtifactAndData(Artifact artifact) {
		log.info("Deleting artifact {}", artifact.getId());
		switch (artifact.getArtifactType()) {
		case EXTERNAL: {
			artifactRepository.delete(artifact);
			break;
		}
		case FILE: {
			try {
				GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
				ObjectId objectId = new ObjectId(artifact.getValue());
				gridFSBucket.delete(objectId);
			} catch (Exception e) {
				log.info("Artifact {}, had no data to delete, proceeding with artifact removal");
			}
			artifactRepository.delete(artifact);
			break;
		}
		default:
			throw new CatalogErrorAPIException("Could not delete artifact, unexpected artifact type: " + artifact.getArtifactType());
		}
	}
	
	private void removeArtifactFromDataset(Artifact artifact) {
		Dataset dataset = datasetService.getDatasetByArtifactForApi(artifact.getId());
		// if the Artifact belonged to a Dataset then remove the Dataset
		if (dataset != null) {
			Dataset datasetWithoutArtifact = Dataset.Builder.newInstance()
					.id(dataset.getId())
					.artifact(null)
					.conformsTo(dataset.getConformsTo())
					.createdBy(dataset.getCreatedBy())
					.creator(dataset.getCreator())
					.description(dataset.getDescription())
					.distribution(dataset.getDistribution())
					.hasPolicy(dataset.getHasPolicy())
					.issued(dataset.getIssued())
					.keyword(dataset.getKeyword())
					.lastModifiedBy(dataset.getLastModifiedBy())
					.modified(dataset.getModified())
					.theme(dataset.getTheme())
					.title(dataset.getTitle())
					.version(dataset.getVersion())
					.build();
			datasetService.updateDataset(dataset, datasetWithoutArtifact);
		}
	}
	
}
