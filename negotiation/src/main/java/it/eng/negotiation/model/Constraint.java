package it.eng.negotiation.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@JsonDeserialize(builder = Constraint.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constraint {
	
	@JsonProperty(DSpaceConstants.ODRL_LEFT_OPERAND)
	private Object leftOperand;
	
	@JsonProperty(DSpaceConstants.ODRL_OPERATOR)
	private Object operator;
	
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
		public Builder leftOperand(Object leftOperand) {
			constraint.leftOperand = leftOperand;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_OPERATOR)
		public Builder operator(Object operator) {
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
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof Constraint)) {
			return false;
		}
		
		// typecast o to Constraint so that we can compare data members 
		Constraint anotherConstraint = (Constraint) o;
		
		LeftOperand thisLeftOperand = extractLeftOperand(this);
		LeftOperand anotherLeftOperator = extractLeftOperand(anotherConstraint);
		if (thisLeftOperand != anotherLeftOperator) {
	            return false;
        }
		
		Operator thisOperator = extractOperator(this);
		Operator anotherOperator = extractOperator(anotherConstraint);
		if (thisOperator != anotherOperator) {
	            return false;
        }

		if(!this.rightOperand.equals(anotherConstraint.getRightOperand())) {
			return false;
		}
		return true;
	}

	private Operator extractOperator(Constraint anotherConstraint) {
		Operator anotherOperator;
		if(anotherConstraint.operator instanceof Map) {
			anotherOperator = Operator.fromString((String) ((Map)anotherConstraint.operator).get(DSpaceConstants.ID));
		} else if(anotherConstraint.operator instanceof Reference) {
			anotherOperator = Operator.fromString(((Reference)anotherConstraint.operator).getId());
		} else if(anotherConstraint.operator instanceof String) {
			anotherOperator = Operator.fromString((String)anotherConstraint.operator);
		} else {
			anotherOperator = (Operator) anotherConstraint.operator;
		}
		return anotherOperator;
	}

	private LeftOperand extractLeftOperand(Constraint anotherConstraint) {
		LeftOperand anotherLeftOperString;
		if(anotherConstraint.leftOperand instanceof Map) {
			anotherLeftOperString = LeftOperand.fromString((String)((Map)anotherConstraint.leftOperand).get(DSpaceConstants.ID));
		} else if(anotherConstraint.leftOperand instanceof Reference) {
			anotherLeftOperString = LeftOperand.fromString(((Reference)anotherConstraint.leftOperand).getId());
		} else if(anotherConstraint.leftOperand instanceof String) {
			anotherLeftOperString = LeftOperand.fromString((String) anotherConstraint.leftOperand);
		} else {
			anotherLeftOperString = (LeftOperand) anotherConstraint.leftOperand;
		}
		return anotherLeftOperString;
	}
	
}
