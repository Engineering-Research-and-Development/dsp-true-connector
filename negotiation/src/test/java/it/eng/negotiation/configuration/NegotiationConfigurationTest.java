package it.eng.negotiation.configuration;

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
class NegotiationConfigurationTest {

    @InjectMocks
    private NegotiationConfiguration config;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(config, "schedulerPoolSize", 5);
    }

    @Test
    @DisplayName("Should create a negotiationTaskScheduler backed by a thread pool")
    void testNegotiationTaskScheduler() {
        TaskScheduler scheduler = config.negotiationTaskScheduler();

        assertNotNull(scheduler);
        assertInstanceOf(ThreadPoolTaskScheduler.class, scheduler);
    }
}
