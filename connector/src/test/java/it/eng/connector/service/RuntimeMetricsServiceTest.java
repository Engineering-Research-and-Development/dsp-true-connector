package it.eng.connector.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMetricsServiceTest {

    @Test
    @DisplayName("Get runtime metrics returns current JVM and process values")
    void getRuntimeMetrics_returnsCurrentValues() {
        RuntimeMetricsService runtimeMetricsService = new RuntimeMetricsService(Optional.empty());

        var metrics = runtimeMetricsService.getRuntimeMetrics();

        assertNotNull(metrics);
        assertTrue(metrics.processCpuUsage() >= -1.0d);
        assertTrue(metrics.systemCpuUsage() >= -1.0d);
        assertTrue(metrics.heapUsedBytes() >= 0L);
        assertTrue(metrics.heapMaxBytes() >= -1L);
        assertTrue(metrics.nonHeapUsedBytes() >= 0L);
        assertTrue(metrics.liveThreadCount() > 0);
        assertTrue(metrics.uptimeMilliseconds() >= 0L);
    }
}
