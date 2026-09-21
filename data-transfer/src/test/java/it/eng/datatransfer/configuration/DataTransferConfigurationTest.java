package it.eng.datatransfer.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class DataTransferConfigurationTest {

    @InjectMocks
    private DataTransferConfiguration config;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(config, "schedulerPoolSize", 5);
    }

    @Test
    @DisplayName("Should create a transferTaskScheduler backed by a thread pool")
    void testTransferTaskScheduler() {
        TaskScheduler scheduler = config.transferTaskScheduler();

        assertNotNull(scheduler);
        assertInstanceOf(ThreadPoolTaskScheduler.class, scheduler);
    }
}
