package it.eng.tools.event.policyenforcement;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ArtifactConsumedEvent {

	private String agreementId;
}
