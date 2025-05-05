package it.eng.tools.service;

import it.eng.tools.exception.ApplicationPropertyErrorException;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.repository.ApplicationPropertiesRepository;
import it.eng.tools.util.ToolsMockObjectUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationPropertiesServiceTest {

	@Mock
	private ApplicationPropertiesRepository repository;
	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@InjectMocks
	private ApplicationPropertiesService service;

	private ApplicationProperty property = ToolsMockObjectUtil.PROPERTY;

	@Test
	@DisplayName("Get application properties successfully")
	void getApplicationProperties_success() {
		when(repository.findAll(Sort.by("id"))).thenReturn(Collections.singletonList(property));
		List<ApplicationProperty> propertiesList = service.getProperties(null);
		assertNotNull(propertiesList);
		verify(repository).findAll(Sort.by("id"));
	}

	@Test
	@DisplayName("Get application property throws exception when not found")
	void getApplicationProperty_notFound() {
		when(repository.findAll(Sort.by("id"))).thenReturn(Collections.emptyList());
		assertThrows(ApplicationPropertyErrorException.class, () -> service.getProperties(null));
	}

	@Test
	@DisplayName("Get application property by Key successfully")
	void getApplicationPropertByKey_success() {
		when(repository.findById(anyString())).thenReturn(Optional.of(property));
		Optional<ApplicationProperty> retrieved = service.getPropertyByKey(property.getKey());
		assertTrue(retrieved.isPresent());
		verify(repository).findById(property.getKey());
	}

	@Test
    @DisplayName("Update application property successfully")
    void updateApplicationProperty_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(property));
        when(repository.save(any(ApplicationProperty.class))).thenReturn(property);

        ApplicationProperty updatedApplicationPropertyData = ToolsMockObjectUtil.APPLICATION_PROPERTY_FOR_UPDATE;
        ApplicationProperty oldUpdatedApplicationPropertyData = ToolsMockObjectUtil.OLD_APPLICATION_PROPERTY_FOR_UPDATE;

        ApplicationProperty updatedApplicationProperty = service.updateProperty(updatedApplicationPropertyData, oldUpdatedApplicationPropertyData);
        assertNotNull(updatedApplicationProperty);
        verify(repository).findById(updatedApplicationProperty.getKey());
        verify(repository).save(any(ApplicationProperty.class));
        verify(applicationEventPublisher).publishEvent(any(ApplicationProperty.class));
    }

}
