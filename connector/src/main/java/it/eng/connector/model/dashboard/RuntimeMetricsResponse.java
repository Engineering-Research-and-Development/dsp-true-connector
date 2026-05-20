package it.eng.connector.model.dashboard;

/**
 * Runtime metrics exposed by the admin dashboard.
 *
 * @param processCpuUsage the process CPU usage ratio, or {@code -1} when unavailable
 * @param systemCpuUsage the system CPU usage ratio, or {@code -1} when unavailable
 * @param heapUsedBytes the used JVM heap in bytes
 * @param heapMaxBytes the maximum JVM heap in bytes
 * @param nonHeapUsedBytes the used JVM non-heap memory in bytes
 * @param liveThreadCount the current live thread count
 * @param uptimeMilliseconds the JVM uptime in milliseconds
 */
public record RuntimeMetricsResponse(
        double processCpuUsage,
        double systemCpuUsage,
        long heapUsedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        int liveThreadCount,
        long uptimeMilliseconds) {
}
