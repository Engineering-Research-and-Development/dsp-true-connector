package it.eng.catalog.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode   // requires for offer check in negotiation flow
@JsonDeserialize(builder = Constraint.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constraint implements Serializable {
	
	private static final long serialVersionUID = 1L;

	@JsonProperty(DSpaceConstants.ODRL_LEFT_OPERAND)
	private LeftOperand leftOperand;
	
	@JsonProperty(DSpaceConstants.ODRL_OPERATOR)
	private Operator operator;
	
	@JsonProperty(DSpaceConstants.ODRL_RIGHT_OPERAND)
	private String rightOperand;

	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private Constraint constraint;

		private Builder() {
			constraint = new Constraint();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.ODRL_LEFT_OPERAND)
		public Builder leftOperand(LeftOperand leftOperand) {
			constraint.leftOperand = leftOperand;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_OPERATOR)
		public Builder operator(Operator operator) {
			constraint.operator = operator;
			return this;
		}

		@JsonProperty(DSpaceConstants.ODRL_RIGHT_OPERAND)
		public Builder rightOperand(String rightOperand) {
			constraint.rightOperand = rightOperand;
			return this;
		}
		
		public Constraint build() {
			return constraint;
		}
	}
}
