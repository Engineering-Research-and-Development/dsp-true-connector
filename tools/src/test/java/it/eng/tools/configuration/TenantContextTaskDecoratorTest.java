package it.eng.tools.configuration;

import it.eng.tools.service.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link TenantContextTaskDecorator}.
 */
class TenantContextTaskDecoratorTest {

    private final TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("decorate_propagatesTenantId: worker receives same tenantId as submitting thread")
    void decorate_propagatesTenantId() throws InterruptedException {
        TenantContextHolder.setTenantId("test-tenant");
        AtomicReference<String> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> captured.set(TenantContextHolder.getTenantId()));

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertEquals("test-tenant", captured.get());
    }

    @Test
    @DisplayName("decorate_clearsTenantIdAfterRun: tenantId is cleared on worker thread even when task throws")
    void decorate_clearsTenantIdAfterRun() throws InterruptedException {
        TenantContextHolder.setTenantId("test-tenant");
        AtomicReference<String> afterRun = new AtomicReference<>("NOT_CLEARED");

        Runnable taskThatThrows = () -> {
            throw new RuntimeException("intentional");
        };
        Runnable decorated = decorator.decorate(taskThatThrows);

        Thread worker = new Thread(() -> {
            try {
                decorated.run();
            } catch (RuntimeException ignored) {
                // expected
            }
            afterRun.set(TenantContextHolder.getTenantId());
        });
        worker.start();
        worker.join();

        assertNull(afterRun.get(), "TenantContextHolder must be cleared after task completion, even on exception");
    }

    @Test
    @DisplayName("decorate_nullTenantId_isHandledGracefully: no NPE when submitting thread has no tenantId")
    void decorate_nullTenantId_isHandledGracefully() throws InterruptedException {
        // no tenantId set on calling thread
        AtomicReference<String> captured = new AtomicReference<>("NOT_SET");

        Runnable decorated = decorator.decorate(() -> captured.set(TenantContextHolder.getTenantId()));

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertNull(captured.get(), "Worker thread should see null tenantId when none was set on submitting thread");
    }
}
