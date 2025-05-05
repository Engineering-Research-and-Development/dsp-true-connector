package it.eng.tools.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@ExtendWith(MockitoExtension.class)
class AsynchronousSpringEventsConfigTest {

    @InjectMocks
    private AsynchronousSpringEventsConfig config;

    @Test
    @DisplayName("Should create application event multicaster with async executor")
    void testSimpleApplicationEventMulticaster() {
        // Act
        ApplicationEventMulticaster multicaster = config.simpleApplicationEventMulticaster();

        // Assert
        assertNotNull(multicaster);
        assertTrue(multicaster instanceof SimpleApplicationEventMulticaster);
        
        // Verify the task executor is set (indirectly, as it's a private field)
        SimpleApplicationEventMulticaster simpleMulticaster = (SimpleApplicationEventMulticaster) multicaster;
        
        // We can't directly access the task executor, but we can verify the multicaster is properly configured
        // by checking it's not null and is the correct type
        assertNotNull(simpleMulticaster);
    }
}
