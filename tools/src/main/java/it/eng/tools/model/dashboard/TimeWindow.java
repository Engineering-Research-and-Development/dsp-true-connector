package it.eng.tools.model.dashboard;

import java.time.Instant;

public record TimeWindow(Instant from, Instant to, String bucket) {
}
