package it.eng.tools.configuration;

import it.eng.tools.service.TenantContextHolder;
import org.springframework.core.task.TaskDecorator;

/**
 * A {@link TaskDecorator} that propagates the current thread's tenant context to async worker threads.
 *
 * <p>{@link TenantContextHolder} stores the tenant identifier in a {@link ThreadLocal}. When
 * Spring dispatches work via an executor or scheduler, the worker thread starts with a blank
 * thread-local context. Applying this decorator to every executor and scheduler that is used
 * outside the HTTP request thread ensures that the captured tenant ID is available in the worker
 * thread during task execution, and is cleared via {@link TenantContextHolder#clear()} in a
 * {@code finally} block to prevent context leakage across pooled threads.
 *
 * <p>This class is stateless and does not require a Spring bean definition.
 * Instantiate it via {@code new TenantContextTaskDecorator()} inside each configuration class
 * that wires an executor or scheduler.
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    /**
     * Captures the calling thread's tenant ID and wraps the supplied task so that the worker
     * thread runs with the same tenant context, clearing it afterwards.
     *
     * @param task the original runnable to decorate
     * @return a new runnable that sets and then clears the tenant context around {@code task.run()}
     */
    @Override
    public Runnable decorate(Runnable task) {
        String tenantId = TenantContextHolder.getTenantId();
        return () -> {
            try {
                TenantContextHolder.setTenantId(tenantId);
                task.run();
            } finally {
                TenantContextHolder.clear();
            }
        };
    }
}
