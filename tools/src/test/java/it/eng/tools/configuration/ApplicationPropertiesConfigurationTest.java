package it.eng.tools.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.repository.ApplicationPropertiesRepository;
import it.eng.tools.service.ApplicationPropertiesService;

@ExtendWith(MockitoExtension.class)
class ApplicationPropertiesConfigurationTest {

    @Mock
    private AbstractEnvironment environment;
    
    @Mock
    private ApplicationPropertiesService service;
    
    @Mock
    private ApplicationPropertiesRepository repository;
    
    @Mock
    private MutablePropertySources propertySources;
    
    @Mock
    private OriginTrackedMapPropertySource propertySource;

    private ApplicationPropertiesConfiguration configuration;
    private Map<String, Object> properties;

    @BeforeEach
    void setUp() {
        // Setup environment mock
        when(environment.getPropertySources()).thenReturn(propertySources);
        
        // Setup property sources
        properties = new HashMap<>();
        properties.put("test.key1", "value1");
        properties.put("test.key2", "value2");
        
        when(propertySources.stream()).thenReturn(Stream.of(propertySource));
        when(propertySource.getName()).thenReturn("application.properties");
        when(propertySource.getSource()).thenReturn(properties);
        
        // Create configuration instance after mocks are set up
        configuration = new ApplicationPropertiesConfiguration(environment, service, repository);
    }

    @Test
    @DisplayName("Should initialize and process properties")
    void testInit() {
        // Arrange
        when(repository.findById("test.key1")).thenReturn(Optional.empty());
        when(repository.findById("test.key2")).thenReturn(Optional.empty());
        
        ApplicationProperty property1 = ApplicationProperty.Builder.newInstance().key("test.key1").value("value1").build();
        ApplicationProperty property2 = ApplicationProperty.Builder.newInstance().key("test.key2").value("value2").build();
        
        when(repository.insert(any(ApplicationProperty.class)))
            .thenReturn(property1)
            .thenReturn(property2);

        // Act
        configuration.init();

        // Assert
        verify(repository, times(2)).findById(anyString());
        verify(repository, times(2)).insert(any(ApplicationProperty.class));
        verify(service, never()).addPropertyOnEnv(anyString(), anyString(), any(Environment.class));
    }

    @Test
    @DisplayName("Should update environment when property value differs")
    void testInitWithDifferentValues() {
        // Arrange
        ApplicationProperty existingProperty = ApplicationProperty.Builder.newInstance().key("test.key1").value("different-value").build();
        when(repository.findById("test.key1")).thenReturn(Optional.of(existingProperty));
        when(repository.findById("test.key2")).thenReturn(Optional.empty());
        
        ApplicationProperty property2 = ApplicationProperty.Builder.newInstance().key("test.key2").value("value2").build();
        when(repository.insert(any(ApplicationProperty.class))).thenReturn(property2);

        // Act
        configuration.init();

        // Assert
        verify(repository, times(2)).findById(anyString());
        verify(repository, times(1)).insert(any(ApplicationProperty.class));
        verify(service, times(1)).addPropertyOnEnv(eq("test.key1"), eq("different-value"), any(Environment.class));
    }

    @Test
    @DisplayName("Should handle empty property source")
    void testInitWithEmptyProperties() {
        // Arrange
        properties.clear();

        // Act
        configuration.init();

        // Assert
        verify(repository, never()).findById(anyString());
        verify(repository, never()).insert(any(ApplicationProperty.class));
    }
}
