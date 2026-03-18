package it.eng.tools.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AsynchronousSpringEventsConfigTest {

    @InjectMocks
    private AsynchronousSpringEventsConfig config;

    @Test
    @DisplayName("Should create application event multicaster backed by a bounded thread pool")
    void testSimpleApplicationEventMulticaster() {
        ApplicationEventMulticaster multicaster = config.simpleApplicationEventMulticaster();

        assertNotNull(multicaster);
        assertInstanceOf(SimpleApplicationEventMulticaster.class, multicaster);
    }

    @Test
    @DisplayName("Should create a TaskScheduler backed by a thread pool")
    void testTaskScheduler() {
        TaskScheduler scheduler = config.taskScheduler();

        assertNotNull(scheduler);
        assertInstanceOf(ThreadPoolTaskScheduler.class, scheduler);
    }
}
