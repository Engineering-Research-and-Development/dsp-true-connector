package it.eng.tools.model.dashboard;

import java.util.List;

public record HistoricalEventMetrics(
        List<KeyCount> countsByEventType,
        List<KeyCount> countsByRole,
        List<TimeBucketCount> countsOverTime,
        long total) {
}
