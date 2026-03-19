package it.eng.tools.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AsynchronousSpringEventsConfigTest {

    @InjectMocks
    private AsynchronousSpringEventsConfig config;

    /**
     * Populates fields that would normally be injected by Spring's {@code @Value} processing.
     * {@code @InjectMocks} does not run the Spring context, so int fields stay at the JVM
     * default of 0 without this setup, causing pool-creation failures.
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(config, "corePoolSize", 5);
        ReflectionTestUtils.setField(config, "maxPoolSize", 20);
        ReflectionTestUtils.setField(config, "queueCapacity", 50);
        ReflectionTestUtils.setField(config, "schedulerPoolSize", 5);
    }

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

    @Test
    @DisplayName("CallerRunsOrDiscardPolicy should run task in caller thread when executor is active")
    void testCallerRunsOrDiscardPolicy_runsInCallerThread() {
        var policy = new AsynchronousSpringEventsConfig.CallerRunsOrDiscardPolicy();
        var executed = new boolean[]{false};

        // Create a live executor so isShutdown() returns false
        var executor = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>());

        policy.rejectedExecution(() -> executed[0] = true, executor);
        executor.shutdown();

        assertNotNull(policy);
        assert executed[0] : "Task should have been run in caller thread";
    }

    @Test
    @DisplayName("CallerRunsOrDiscardPolicy should discard task when executor is shut down")
    void testCallerRunsOrDiscardPolicy_discardsWhenShutDown() {
        var policy = new AsynchronousSpringEventsConfig.CallerRunsOrDiscardPolicy();
        var executed = new boolean[]{false};

        var executor = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>());
        executor.shutdown();

        policy.rejectedExecution(() -> executed[0] = true, executor);

        assert !executed[0] : "Task should have been discarded when executor is shut down";
    }
}
