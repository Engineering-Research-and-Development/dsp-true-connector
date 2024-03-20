package it.eng.negotiation.event;

import it.eng.negotiation.model.ContractNegotiation;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContractNegotiationEvent {

	private ContractNegotiation contractNegotiation;
	private String user;
	private String action;
	private String description;
	
	
}
