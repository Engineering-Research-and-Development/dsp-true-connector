package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import it.eng.catalog.repository.DatasetRepository;
import it.eng.tools.model.Artifact;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.util.MockObjectUtil;

@ExtendWith(MockitoExtension.class)
public class ArtifactServiceTest {
	
	@Mock
	private MongoTemplate mongoTemplate;
	@Mock
	private DatasetService datasetService;
	@Mock
	private ArtifactRepository artifactRepository;
	@Mock
	private DatasetRepository datasetRepository;

	@InjectMocks
	private ArtifactService artifactService;
	
	@Test
    @DisplayName("Get artifacts by id - success")
    public void getArtifactById_success() {
		when(artifactRepository.findById(MockObjectUtil.ARTIFACT_FILE.getId())).thenReturn(Optional.of(MockObjectUtil.ARTIFACT_FILE));

		List<Artifact> result = artifactService.getArtifacts(MockObjectUtil.ARTIFACT_FILE.getId());

        assertEquals(MockObjectUtil.ARTIFACT_FILE.getId(), result.get(0).getId());
        verify(artifactRepository).findById(MockObjectUtil.ARTIFACT_FILE.getId());
    }
	
	@Test
    @DisplayName("Get all artifacts - success")
    public void getAllArtifacts_success() {
		when(artifactRepository.findAll())
		.thenReturn(List.of(MockObjectUtil.ARTIFACT_FILE, MockObjectUtil.ARTIFACT_EXTERNAL));

		List<Artifact> result = artifactService.getArtifacts(null);

		assertEquals(2, result.size());
        assertEquals(MockObjectUtil.ARTIFACT_FILE.getId(), result.get(0).getId());
        verify(artifactRepository).findAll();
    }

}
