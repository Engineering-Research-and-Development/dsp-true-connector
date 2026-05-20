package it.eng.connector.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import it.eng.connector.model.dashboard.RuntimeMetricsResponse;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Provides runtime metrics for the admin dashboard.
 */
@Service
public class RuntimeMetricsService {

    private static final String PROCESS_CPU_USAGE_METER = "process.cpu.usage";
    private static final String SYSTEM_CPU_USAGE_METER = "system.cpu.usage";
    private static final double UNAVAILABLE_CPU_VALUE = -1.0d;

    private final MeterRegistry meterRegistry;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final RuntimeMXBean runtimeMXBean;
    private final java.lang.management.OperatingSystemMXBean operatingSystemMXBean;

    public RuntimeMetricsService(Optional<MeterRegistry> meterRegistry) {
        this(
                meterRegistry.orElse(null),
                ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getThreadMXBean(),
                ManagementFactory.getRuntimeMXBean(),
                ManagementFactory.getOperatingSystemMXBean()
        );
    }

    RuntimeMetricsService(
            MeterRegistry meterRegistry,
            MemoryMXBean memoryMXBean,
            ThreadMXBean threadMXBean,
            RuntimeMXBean runtimeMXBean,
            java.lang.management.OperatingSystemMXBean operatingSystemMXBean) {
        this.meterRegistry = meterRegistry;
        this.memoryMXBean = memoryMXBean;
        this.threadMXBean = threadMXBean;
        this.runtimeMXBean = runtimeMXBean;
        this.operatingSystemMXBean = operatingSystemMXBean;
    }

    /**
     * Returns the current runtime metrics for the connector process.
     *
     * @return the current runtime metrics snapshot
     */
    public RuntimeMetricsResponse getRuntimeMetrics() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        return new RuntimeMetricsResponse(
                getProcessCpuUsage(),
                getSystemCpuUsage(),
                heapUsage.getUsed(),
                heapUsage.getMax(),
                nonHeapUsage.getUsed(),
                threadMXBean.getThreadCount(),
                runtimeMXBean.getUptime()
        );
    }

    private double getProcessCpuUsage() {
        return getCpuUsage(PROCESS_CPU_USAGE_METER, "getProcessCpuLoad");
    }

    private double getSystemCpuUsage() {
        double meterValue = readGaugeValue(SYSTEM_CPU_USAGE_METER);
        if (meterValue != UNAVAILABLE_CPU_VALUE) {
            return meterValue;
        }
        double cpuLoad = invokeCpuMethod("getCpuLoad");
        if (cpuLoad != UNAVAILABLE_CPU_VALUE) {
            return cpuLoad;
        }
        return invokeCpuMethod("getSystemCpuLoad");
    }

    private double getCpuUsage(String meterName, String fallbackMethodName) {
        double meterValue = readGaugeValue(meterName);
        if (meterValue != UNAVAILABLE_CPU_VALUE) {
            return meterValue;
        }
        return invokeCpuMethod(fallbackMethodName);
    }

    private double readGaugeValue(String meterName) {
        if (meterRegistry == null) {
            return UNAVAILABLE_CPU_VALUE;
        }
        Gauge gauge = meterRegistry.find(meterName).gauge();
        if (gauge == null) {
            return UNAVAILABLE_CPU_VALUE;
        }
        return normalizeCpuValue(gauge.value());
    }

    private double invokeCpuMethod(String methodName) {
        try {
            Method method = operatingSystemMXBean.getClass().getMethod(methodName);
            Object value = method.invoke(operatingSystemMXBean);
            if (value instanceof Number number) {
                return normalizeCpuValue(number.doubleValue());
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            return UNAVAILABLE_CPU_VALUE;
        }
        return UNAVAILABLE_CPU_VALUE;
    }

    private double normalizeCpuValue(double value) {
        if (!Double.isFinite(value) || value < 0.0d) {
            return UNAVAILABLE_CPU_VALUE;
        }
        return value;
    }
}
