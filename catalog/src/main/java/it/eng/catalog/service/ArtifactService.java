package it.eng.catalog.service;

import java.util.List;
import java.util.Optional;

import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.util.ToolsUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ArtifactService {

	private final ArtifactRepository artifactRepository;
	private final S3ClientService s3ClientService;
	private final S3Properties s3Properties;
	
	public ArtifactService(ArtifactRepository artifactRepository, S3ClientService s3ClientService, S3Properties s3Properties) {
		super();
		this.artifactRepository = artifactRepository;
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
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

	public Artifact uploadArtifact(MultipartFile file, String externalURL, String authorization) {
		Artifact artifact = null;
		if (file != null) {
			String fileId = storeFile(file);
			artifact = Artifact.Builder.newInstance()
					.artifactType(ArtifactType.FILE)
					.value(fileId)
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
				s3ClientService.deleteFile(s3Properties.getBucketName(), artifact.getValue());
			} catch (Exception e) {
				log.warn("Error while deleting file from S3: {}", e.getMessage());
			}
			break;
		}
		default:
			break;
		}
		artifactRepository.delete(artifact);
	}

	private String storeFile(MultipartFile file) {
		// Create bucket if it doesn't exist
		if (!s3ClientService.bucketExists(s3Properties.getBucketName())) {
			s3ClientService.createBucket(s3Properties.getBucketName());
		}

		ContentDisposition contentDisposition = ContentDisposition.attachment()
				.filename(file.getOriginalFilename())
				.build();

		String fileId = ToolsUtil.generateUniqueId();

		// Upload file to S3
		try {
			s3ClientService.uploadFile(s3Properties.getBucketName(), fileId, file.getBytes(),
					file.getContentType(), contentDisposition.toString());
		} catch (Exception e) {
			log.error("File storing aborted", e);
			throw new CatalogErrorAPIException("File storing aborted, " + e.getLocalizedMessage());
		}
		log.info("Stored file {} under id {}", file.getOriginalFilename() ,fileId);

		return fileId;
	}
}
