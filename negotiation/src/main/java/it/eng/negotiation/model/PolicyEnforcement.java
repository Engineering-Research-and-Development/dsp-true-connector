package it.eng.negotiation.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "policy_enforcements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEnforcement {

	@Id
	private String id;
	
	private String agreementId;
	private int count;
}
