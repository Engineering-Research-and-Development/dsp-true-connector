package it.eng.tools.model.dashboard;

import java.util.List;

public record TransferSnapshotMetrics(
        List<KeyCount> countsByState,
        List<KeyCount> countsByRoleAndState,
        List<KeyCount> countsByFormat,
        List<KeyCount> countsByDownloadFlag,
        long total) {
}
