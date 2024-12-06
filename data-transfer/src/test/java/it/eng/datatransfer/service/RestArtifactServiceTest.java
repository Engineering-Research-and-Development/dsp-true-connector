package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;

import it.eng.datatransfer.service.api.RestArtifactService;
import it.eng.datatransfer.util.MockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.repository.ArtifactRepository;

@ExtendWith(MockitoExtension.class)
public class RestArtifactServiceTest {
	@Mock
	private DataTransferService dataTransferService;
	@Mock
	private MongoTemplate mongoTemplate;
	@Mock
	private ApplicationEventPublisher publisher;
    @Mock
	private ArtifactRepository artifactRepository;
    @Mock
    private OkHttpRestClient okHttpRestClient;

	@InjectMocks
	private RestArtifactService restArtifactService;
	
	@Test
	@DisplayName("Get artifact - success")
	public void getArtifact_success() {
		
	}
	
}
