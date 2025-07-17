package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

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
        if (StringUtils.isNotBlank(artifactId)) {
            Optional<Artifact> artifact = artifactRepository.findById(artifactId);
            if (artifact.isPresent()) {
                return List.of(artifact.get());
            } else {
                throw new ResourceNotFoundAPIException("Artifact with id " + artifactId + " not found");
            }
        }
        return artifactRepository.findAll();
    }

    public Artifact uploadArtifact(String fileId, MultipartFile file, String externalURL, String authorization) {
        Artifact artifact = null;
        if (file != null) {
            storeFile(fileId, file);
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

    private String storeFile(String fileId, MultipartFile file) {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.getOriginalFilename())
                .build();

        // Upload file to S3
        try {
            s3ClientService.uploadFile(
                    file.getInputStream(),
                    s3Properties.getBucketName(),
                    fileId,
                    file.getContentType(),
                    contentDisposition.toString()
            ).get();
            //TODO make also uploading async
        } catch (Exception e) {
            log.error("File storing aborted", e);
            throw new CatalogErrorAPIException("File storing aborted, " + e.getLocalizedMessage());
        }
        log.info("Stored file {} under id {}", file.getOriginalFilename(), fileId);

        return fileId;
    }
}
