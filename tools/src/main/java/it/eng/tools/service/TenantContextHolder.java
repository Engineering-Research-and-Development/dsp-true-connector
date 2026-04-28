package it.eng.tools.service;

import org.slf4j.MDC;

/**
 * Thread-local holder for the current request's tenant identifier.
 * Setting the tenant ID also propagates it to the SLF4J {@link MDC} under the key
 * {@code tenantId}, so all log statements emitted during the request automatically
 * carry tenant context without any change to individual logger call sites.
 * Should be set at the start of each request and cleared after it completes.
 */
public class TenantContextHolder {

    /** MDC key used for tenant-scoped log correlation. */
    public static final String MDC_TENANT_KEY = "tenantId";

    private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    /**
     * Sets the tenant identifier for the current thread and puts it into the SLF4J MDC.
     *
     * @param tenantId the tenant ID to associate with the current thread
     */
    public static void setTenantId(String tenantId) {
        TENANT_ID_HOLDER.set(tenantId);
        if (tenantId != null) {
            MDC.put(MDC_TENANT_KEY, tenantId);
        } else {
            MDC.remove(MDC_TENANT_KEY);
        }
    }

    /**
     * Returns the tenant identifier for the current thread.
     * May be {@code null} for super-admin requests that are not scoped to a tenant.
     *
     * @return the current thread's tenant ID, or {@code null} if not set
     */
    public static String getTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    /**
     * Removes the tenant identifier from the current thread's context and from the SLF4J MDC.
     * Must be called at the end of each request to prevent memory leaks.
     */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
        MDC.remove(MDC_TENANT_KEY);
    }
}
