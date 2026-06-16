package it.eng.tools.event.policyenforcement;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ArtifactTransferredEvent {
    private String agreementId;
}
