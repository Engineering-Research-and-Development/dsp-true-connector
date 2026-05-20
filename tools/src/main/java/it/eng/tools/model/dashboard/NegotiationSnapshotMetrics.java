package it.eng.tools.model.dashboard;

import java.util.List;

public record NegotiationSnapshotMetrics(
        List<KeyCount> countsByState,
        List<KeyCount> countsByRoleAndState,
        long total) {
}
