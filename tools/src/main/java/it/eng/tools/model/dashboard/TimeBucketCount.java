package it.eng.tools.model.dashboard;

import java.time.Instant;

public record TimeBucketCount(Instant bucketStart, String key, long count) {
}
