package it.eng.negotiation.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "policy_enforcements")
@Data
public class PolicyEnforcement {

	@Id
	private String id;
	
	private String agreementId;
	private int count;
}
